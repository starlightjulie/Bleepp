# How Bleepp makes sound on a silent phone

No root, no LSPatch, no system hacks. Bleepp reads notifications with Android's normal notification access, then plays the sound itself on an audio stream the silent switch doesn't touch.

I tested each route by ear on a Galaxy S25 Ultra (Android 16) and a Moto G 5G 2024 (Android 15):

| Phone state | Alarm audio | Accessibility audio |
| --- | --- | --- |
| Silent or vibrate | Plays | Plays |
| Do Not Disturb, Priority | Plays | Plays |
| Do Not Disturb, Alarms only | Plays | Plays |
| Do Not Disturb, Total Silence | Muted | Plays |

Bleepp uses alarm audio by default. Accessibility audio is only used when you turn on "Break through Total Silence" for an app and Total Silence is really on.

## Things that surprised me

- **The alarm stream can be silent at its lowest step.** On the Moto, step 1 of 7 is `-inf` dB, so a phone with its alarm volume all the way down plays nothing at all. Bleepp raises the volume for each sound and puts it back straight after. It saves the old volume first, so a crash can't leave your alarm stuck loud.
- **Alarm-class notification channels don't work.** A channel with alarm audio is quietly downgraded to normal notification audio unless the notification is in the alarm category, and even then neither phone made a sound.
- **"Playing" doesn't mean audible.** Android reports a player as started even while it's muted. The field that matters is `mutedState` in `dumpsys audio`.
- **Apps can't turn Do Not Disturb off any more.** Since Android 15, an app calling `setInterruptionFilter` only changes its own rule, which is why the accessibility route exists.

## Calls

A ringing call loops its sound for up to a minute and stops as soon as the call's notification is removed or changes into an ongoing call. Spotting a ringing call had a surprise of its own:

- **Android hides the ringtone usage from listeners.** Google Voice's Incoming call channel is stored with ringtone audio, but Android 16 shows listeners plain notification audio instead, unless the notification uses the call style. The channel's sound still points at the ringtone, so that's what Bleepp checks, along with the call style when an app uses it.

## Android 17

Android 17 silences audio from background apps with no error at all, unless the app has a foreground service. So every alert plays inside a media foreground service that lasts exactly as long as the sound. The app also holds the exact alarm permission, which Android 17 uses to waive its stricter rule for alarm audio.

This follows Google's published rules, but neither test phone runs Android 17 yet, so it isn't proven on real hardware.

## Limits

- Accessibility audio follows media volume, and Total Silence drops that to zero as far as apps can see, so the level there can't be set from the app's volume slider.
- Android 15 and newer can hide one-time codes from notification apps, so those may show up in the log without the code.
- Android can't save tap actions to disk. Tapping a log entry opens the exact place while the notification is still showing or Bleepp hasn't restarted. After that, it opens the app instead.
