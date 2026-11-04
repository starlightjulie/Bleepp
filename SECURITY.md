# Security

## Where to get Bleepp

The only official place to download Bleepp is the [Releases page](../../releases) of this repo. If I publish it somewhere else later (F-Droid, Obtainium, Google Play), I'll list it here and in the README myself.

Copies on other sites weren't uploaded by me and may be old or modified. Bleepp can read every notification on your phone, so please only get it from a source you trust.

## Checking your copy is official

Every official release is signed with my Bleepp release key. Its SHA-256 certificate fingerprint is:

```
CA:12:27:72:9B:10:7C:0E:F9:0E:2D:48:E0:DC:F6:D4:7D:A6:04:B7:F0:FB:76:E8:3A:B0:A7:93:E3:F4:BA:74
```

You can check it two ways:

- **In the app:** Settings → About shows **Official build**. A copy signed by anyone else shows **Unofficial build** instead.
- **On a computer:** `apksigner verify --print-certs Bleepp.apk` and compare the `SHA-256 digest` line with the fingerprint above.

Android also won't let a differently signed copy install over an official one, so if an "update" from somewhere else refuses to install, that's why.

## What Bleepp can see

Notification access lets Bleepp read the text of everything that reaches your notification shade. That's the whole point of the log, but it's a lot of trust, so here's exactly what happens to it:

- The log is stored in the app's private storage on your phone, and old entries are deleted after the number of days you pick.
- It's excluded from Android backups and device transfers, so it can't end up in a cloud backup.
- Nothing in the log is ever sent anywhere. Bleepp has no servers, analytics, ads or trackers, and doesn't use Google Play Services.

## What Bleepp connects to

- **GitHub's public API**, every few hours (you choose how often), to see if there's a newer release. You can turn this off in Settings → Updates. Nothing about you or your notifications is sent.

That's the only network traffic it makes.

## Reporting a problem

If you find a security issue, please don't open a public issue. Use GitHub's [private vulnerability reporting](../../security/advisories/new) instead, and I'll get back to you as soon as I can.
