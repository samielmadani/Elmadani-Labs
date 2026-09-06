# Elmadani Labs

Private Android app catalogue backed by public GitHub Releases.

## Build

Open the project in Android Studio, or run `./gradlew assembleDebug` from Windows, Linux, macOS, or Termux after generating the Gradle wrapper. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Edit only the `githubUsername` value in `app/src/main/assets/apps.json` if the account changes. Repositories are discovered automatically from that account through the GitHub REST API; no application list, APK URL, package name, or version number is maintained in Elmadani Labs.

The app loads cached release metadata first, then synchronizes repository and release pages in the background using pagination and ETags. Only published releases with APK assets enter the catalogue. APK binaries are downloaded only after the user selects INSTALL or UPDATE.

Release builds use a configured release keystore through `ELMADANI_KEYSTORE_PATH`, `ELMADANI_KEYSTORE_PASSWORD`, `ELMADANI_KEY_ALIAS`, and `ELMADANI_KEY_PASSWORD`. Without those variables, local builds use the Android debug signing key for development.