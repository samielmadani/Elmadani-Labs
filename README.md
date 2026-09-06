# Elmadani

Private Android app catalogue backed by public GitHub Releases.

## Build

Open the project in Android Studio, or run `./gradlew assembleDebug` from Windows, Linux, macOS, or Termux after generating the Gradle wrapper. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Edit only the `githubUsername` value in `app/src/main/assets/apps.json` if the account changes. Repositories are discovered automatically from that account through the GitHub REST API; no application list, APK URL, package name, or version number is maintained in Elmadani Labs.

The app loads cached release metadata first, then synchronizes repository and release pages in the background using pagination and ETags. Only published releases with APK assets enter the catalogue. APK binaries are downloaded only after the user selects INSTALL or UPDATE.

Release builds require a configured production keystore through `ELMADANI_KEYSTORE_PATH`, `ELMADANI_KEYSTORE_PASSWORD`, `ELMADANI_KEY_ALIAS`, and `ELMADANI_KEY_PASSWORD`. Debug builds remain available locally without those variables.

The current configuration is `samielmadani` and `samielmadani/Elmadani-Labs`; the account must expose repositories and the self repository must have a published APK release before the catalogue or self-update screen can show data.

Private repositories are supported by saving a GitHub personal access token in Settings. Use a fine-grained token with read-only Metadata and Contents access to the repositories, or a classic token with the `repo` scope. The token is stored with Android Keystore-backed encryption and is never bundled in the APK or printed by the app.

## Automatic Releases

Pushes to `master` run `.github/workflows/release.yml`. The workflow reads the Gradle `versionName` and `versionCode`, builds `app/build/outputs/apk/release/app-release.apk`, and publishes `elmadani-labs-<versionName>.apk` to a GitHub Release tagged `v<versionName>`. If that tag already exists, it increments the patch version and versionCode in the runner workspace for that release build, without committing generated APKs or version changes back to the repository.

Production release builds require these GitHub Actions secrets:

- `ELMADANI_KEYSTORE_BASE64`
- `ELMADANI_KEYSTORE_PASSWORD`
- `ELMADANI_KEY_ALIAS`
- `ELMADANI_KEY_PASSWORD`

To activate the workflow from the current branch:

```powershell
git add .github/workflows/release.yml README.md
git commit -m "Add automatic master branch releases"
git push -u origin master
```