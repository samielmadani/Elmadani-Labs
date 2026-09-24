# Elmadani Store

Private Android app catalogue backed by GitHub Releases. It discovers APK releases from repositories owned by `samielmadani`, shows release notes, and downloads APKs directly to Android's installer.

## Build

Open the project in Android Studio, or run `./gradlew assembleDebug` from Windows, Linux, macOS, or Termux after generating the Gradle wrapper. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The application ID is `com.samielmadani.elmadanistore`. Add a GitHub personal access token in Settings when higher API limits or private repositories are needed. The token stays on the device.

The app supports dynamic colors, Light, Dark, OLED, manual accent colors, sorting, cached APK cleanup, background release checks, update notifications, and per-app display-name overrides.

GitHub Releases publish the unsigned debug APK for manual installation. Android may require allowing installs from unknown sources.

The current configuration is `samielmadani` and `samielmadani/Elmadani-Labs`; the account must expose repositories and the self repository must have a published APK release before the catalogue or self-update screen can show data.

Private repositories are supported by saving a GitHub personal access token in Settings. Use a fine-grained token with read-only Metadata and Contents access to the repositories, or a classic token with the `repo` scope. The token is stored with Android Keystore-backed encryption and is never bundled in the APK or printed by the app.

## Automatic Releases

Pushes to `master` run `.github/workflows/release.yml`. The workflow reads the Gradle `versionName` and `versionCode`, builds `app/build/outputs/apk/debug/app-debug.apk`, and publishes it as `elmadani-store-<versionName>.apk` to a GitHub Release tagged `v<versionName>`.

To activate the workflow from the current branch:

```powershell
git add .github/workflows/release.yml README.md
git commit -m "Add automatic master branch releases"
git push -u origin master
```