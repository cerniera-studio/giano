# Giano

The Fold's outward face: while you work on the main screen, the cover screen on the back faces the room.
A red on-air lamp during calls, or a large card for the person opposite you. Built on Cerniera's
[Kit](https://github.com/cerniera-studio/kit) to the Bellezza Necessaria mandate.

**Now: the probe.** Giano can only exist if the Galaxy Z Fold 8 Ultra lets an app show something on the
cover screen while the main screen is in use (Jetpack WindowManager's dual-screen mode). The first build
asks Android, live as the phone folds and unfolds, lets you try dual-screen and rear display, and keeps
a report to share.

GitHub Actions builds the APK on every push, opens it on an emulator in the Fold's four shapes, and
publishes each build as a release with `Giano.apk` (branch builds as pre-releases).
