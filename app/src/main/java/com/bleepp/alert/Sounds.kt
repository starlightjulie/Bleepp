package com.bleepp.alert

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.bleepp.R
import com.bleepp.data.PHONE_RINGTONE

class BuiltInSound(val id: String, @StringRes val label: Int, @RawRes val raw: Int)

object Sounds {
    val builtIn = listOf(
        BuiltInSound("bleep", R.string.snd_bleep, R.raw.snd_bleep),
        BuiltInSound("pager", R.string.snd_pager, R.raw.snd_pager),
        BuiltInSound("sonar", R.string.snd_sonar, R.raw.snd_sonar),
        BuiltInSound("harbour_bell", R.string.snd_harbour_bell, R.raw.snd_harbour_bell),
        BuiltInSound("rising", R.string.snd_rising, R.raw.snd_rising),
        BuiltInSound("foghorn", R.string.snd_foghorn, R.raw.snd_foghorn),
        BuiltInSound("drops", R.string.snd_drops, R.raw.snd_drops),
        BuiltInSound("morse", R.string.snd_morse, R.raw.snd_morse),
        BuiltInSound("ring", R.string.snd_ring, R.raw.snd_ring),
    )

    fun isCustom(sound: String) = sound.startsWith("content:") || sound.startsWith("file:")

    fun uri(context: Context, sound: String): Uri {
        if (sound == PHONE_RINGTONE) {
            return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) ?: builtIn("ring", context)
        }
        if (isCustom(sound)) return Uri.parse(sound)
        return builtIn(sound, context)
    }

    /** What plays when the chosen sound can't be: the Ring sound for calls, Bleep for everything else. */
    fun fallback(context: Context, call: Boolean = false): Uri = builtIn(if (call) "ring" else "bleep", context)

    private fun builtIn(id: String, context: Context): Uri {
        val raw = (builtIn.firstOrNull { it.id == id } ?: builtIn.first()).raw
        return Uri.parse("android.resource://${context.packageName}/$raw")
    }
}
