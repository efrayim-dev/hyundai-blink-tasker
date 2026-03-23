# Hyundai BlueLink Tasker (US)

Unofficial Android app for **Hyundai US** MyHyundai / BlueLink: unlock, lock, remote start/stop from the app or via **Tasker** (broadcast intent + shared secret).

**Not affiliated with Hyundai.** Same general approach as community libraries such as [bluelinky](https://github.com/Hacksore/bluelinky). APIs can change without notice.

## Build

Open in Android Studio or run:

```bash
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/`.

CI builds on push to `main` and uploads the debug APK as a workflow artifact.

## Tasker

Use **Send Intent** with the action, package, class, and extras documented in the app under **Tasker setup**.
