package com.bleepp.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.bleepp.data.Settings
import kotlin.math.ceil

/** One alert to play: everything the player needs, taken from the app's rule. */
data class AlertRequest(
    val key: String,
    val label: String,
    val sound: String,
    val volumePercent: Int,
    val route: AlertRoute,
    val repeat: Boolean = false,
    val repeatEverySeconds: Int = 30,
    val repeatMax: Int = 1,
    val vibrate: Boolean = false,
    /** Loop the sound for up to this long, as a ringing call does. 0 plays it once. */
    val ringMillis: Long = 0,
) {
    val isCall get() = ringMillis > 0
}

/**
 * Plays alerts on the main thread, one at a time; a new alert replaces the current one, except that
 * an ordinary alert never interrupts a ringing call.
 *
 * The stream volume is raised only while a sound plays and put back after each play, because
 * the alarm stream can sit at a level that's truly silent (index 1 of 7 on a Moto G is -inf dB).
 */
object AlertPlayer {
    private const val MAX_PLAY_MILLIS = 30_000L
    const val CALL_RING_MILLIS = 60_000L
    private val main = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var current: AlertRequest? = null
    private var plays = 0
    private var focus: AudioFocusRequest? = null
    private var restore: Pair<Int, Int>? = null
    private var onIdle: (() -> Unit)? = null
    private var appContext: Context? = null
    private var vibrating = false

    val isPlaying get() = current != null

    fun start(context: Context, request: AlertRequest, idle: () -> Unit) {
        val app = context.applicationContext
        main.post {
            val playing = current
            if (playing != null && playing.isCall && !request.isCall) {
                // Let the call keep ringing; this alert's service has nothing left to do
                if (idle !== onIdle) idle()
                return@post
            }
            if (playing != null && playing.isCall && playing.key == request.key) return@post
            halt(app)
            // The alert being replaced has no sound of its own now, so its service can stop
            val replaced = onIdle
            appContext = app
            current = request
            plays = 0
            onIdle = idle
            if (replaced != null && replaced !== idle) replaced()
            playOnce(app, request)
        }
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        main.post { finish(app) }
    }

    /** The notification behind a repeating alert was opened or cleared, so it has been seen. */
    fun stopFor(context: Context, key: String) {
        val app = context.applicationContext
        main.post { if (current?.key == key) finish(app) }
    }

    /** A call stopped ringing: answered, declined, missed or cancelled by its app. */
    fun stopCall(context: Context, key: String) {
        val app = context.applicationContext
        main.post { if (current?.key == key && current?.isCall == true) finish(app) }
    }

    fun isRingingFor(key: String): Boolean = current?.let { it.isCall && it.key == key } == true

    /** Puts back a volume left raised by a process that died mid-alert. */
    fun recoverVolume(context: Context) {
        val settings = Settings(context)
        val saved = settings.pendingVolumeRestore ?: return
        val parts = saved.split(':').mapNotNull { it.toIntOrNull() }
        if (parts.size == 2 && !isPlaying) {
            context.getSystemService(AudioManager::class.java).setStreamVolume(parts[0], parts[1], 0)
        }
        settings.pendingVolumeRestore = null
    }

    fun targetIndex(max: Int, percent: Int): Int =
        ceil(max * percent.coerceIn(1, 100) / 100.0).toInt().coerceIn(1, max)

    private fun attributes(route: AlertRoute) = AudioAttributes.Builder()
        .setUsage(
            if (route == AlertRoute.ALARM) AudioAttributes.USAGE_ALARM
            else AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
        )
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun playOnce(context: Context, request: AlertRequest) {
        plays++
        raiseVolume(context, request)
        requestFocus(context, request)
        if (request.vibrate) vibrate(context, request)
        val started = open(context, request, Sounds.uri(context, request.sound)) ||
            open(context, request, Sounds.fallback(context, request.isCall))
        if (!started) finish(context)
    }

    /** False if the file can't be played, for example a custom sound that's been deleted. */
    private fun open(context: Context, request: AlertRequest, uri: Uri): Boolean = runCatching {
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(attributes(request.route))
        mp.setDataSource(context, uri)
        mp.isLooping = request.isCall
        mp.setOnCompletionListener { afterPlay(context, request) }
        mp.setOnErrorListener { _, _, _ ->
            afterPlay(context, request)
            true
        }
        mp.prepare()
        mp.start()
        main.postDelayed(cutOff, if (request.isCall) request.ringMillis else MAX_PLAY_MILLIS)
        true
    }.getOrElse {
        releasePlayer()
        false
    }

    private val cutOff = Runnable {
        val context = appContext
        val request = current
        if (context != null && request != null) {
            if (request.isCall) finish(context) else afterPlay(context, request)
        }
    }

    private fun afterPlay(context: Context, request: AlertRequest) {
        releasePlayer()
        restoreVolume(context)
        abandonFocus(context)
        if (current !== request) return
        if (request.repeat && !request.isCall && plays < request.repeatMax) {
            main.postDelayed(next, request.repeatEverySeconds * 1000L)
        } else {
            finish(context)
        }
    }

    private val next = Runnable {
        val request = current
        val context = appContext
        if (request != null && context != null) playOnce(context, request)
    }

    private fun raiseVolume(context: Context, request: AlertRequest) {
        val am = context.getSystemService(AudioManager::class.java)
        // Accessibility audio follows the media stream, and apps can't set its own volume
        val stream = if (request.route == AlertRoute.ALARM) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
        val before = am.getStreamVolume(stream)
        val target = targetIndex(am.getStreamMaxVolume(stream), request.volumePercent)
        if (target == before) return
        restore = stream to before
        Settings(context).pendingVolumeRestore = "$stream:$before"
        runCatching { am.setStreamVolume(stream, target, 0) }
    }

    private fun restoreVolume(context: Context) {
        val (stream, index) = restore ?: return
        restore = null
        val am = context.getSystemService(AudioManager::class.java)
        runCatching { am.setStreamVolume(stream, index, 0) }
        Settings(context).pendingVolumeRestore = null
    }

    private fun requestFocus(context: Context, request: AlertRequest) {
        val am = context.getSystemService(AudioManager::class.java)
        val ask = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes(request.route))
            .build()
        focus = ask
        runCatching { am.requestAudioFocus(ask) }
    }

    private fun abandonFocus(context: Context) {
        focus?.let { context.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun vibrate(context: Context, request: AlertRequest) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        // A call buzzes like a phone ringing, over and over until it stops
        val effect = if (request.isCall) {
            VibrationEffect.createWaveform(longArrayOf(0, 800, 600, 800, 1400), 0)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1)
        }
        vibrating = true
        @Suppress("DEPRECATION")
        vibrator.vibrate(effect, attributes(request.route))
    }

    private fun stopVibrating(context: Context) {
        if (!vibrating) return
        vibrating = false
        context.getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun releasePlayer() {
        main.removeCallbacks(cutOff)
        player?.let { runCatching { it.release() } }
        player = null
    }

    /** Ends the current sound without telling anyone, ready for a new one to take over. */
    private fun halt(context: Context) {
        main.removeCallbacks(next)
        releasePlayer()
        stopVibrating(context)
        restoreVolume(context)
        abandonFocus(context)
    }

    private fun finish(context: Context) {
        halt(context)
        current = null
        val idle = onIdle
        onIdle = null
        idle?.invoke()
    }
}
