# Hyundai BlueLink Tasker (US)

Unofficial Android app for **Hyundai US** MyHyundai / BlueLink: unlock, lock, remote start/stop from the app or via **Tasker** (broadcast intent + shared secret).

**Not affiliated with Hyundai.** Same general approach as community libraries such as [bluelinky](https://github.com/Hacksore/bluelinky). APIs can change without notice.

## Build

Open in Android Studio or run:

```bash
./gradlew assembleRelease
```

Release APK (renamed by Gradle): `app/build/outputs/apk/release/HyundaiBlueLinkTasker-v{version}-release.apk` (e.g. `HyundaiBlueLinkTasker-v1.1.0-release.apk`).

CI builds on every push to `main` (workflow **Build APK**). Open [Actions](https://github.com/efrayim-dev/hyundai-blink-tasker/actions), select the latest run, and download the **HyundaiBlueLinkTasker-release-apk** artifact.

## Tasker

Use **Send Intent** with the action, package, class, and extras documented in the app under **Tasker setup**.
