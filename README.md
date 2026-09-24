# Elmadani Store

> A small Android catalogue for discovering and installing APKs from GitHub Releases.

## ✨ Why this app?

Elmadani Store turns a collection of GitHub-hosted Android projects into one readable catalogue. It shows the latest downloadable APK, release notes, version history, and install state, so updating personal apps does not require opening every repository by hand.

## 📲 How to install

Download the latest APK from the [GitHub Releases page](https://github.com/samielmadani/Elmadani-Store/releases/latest).

1. Download the `.apk` file on an Android device.
2. If Android asks, allow your browser or file manager to install unknown apps.
3. Open the APK and tap **Install**.

## 🔧 Features

- **GitHub catalogue** — discovers repositories owned by `samielmadani` that publish APK assets.
- **Release details** — shows release notes, APK size, version history, and a link back to GitHub.
- **Direct installation** — downloads an APK and hands it to Android's package installer.
- **Update checks** — supports periodic background checks and update notifications.
- **GitHub access** — accepts a personal access token for private repositories and higher API limits.
- **Rate-limit visibility** — caches GitHub responses with ETags, reports remaining quota and reset time, and explains rate-limit failures.
- **Personalisation** — supports Material You dynamic colors, light/dark/OLED themes, accent colors, sorting, and per-repository display-name overrides.
- **Repository metadata** — uses a root `store.json` or `elmadani-store.json` file for a friendly app name and description.
- **Local maintenance** — lets you clear downloaded APKs and ignore repositories from Settings.

## 🗂️ Repository metadata

Add one of these files to the root of a tracked repository:

```json
{
	"name": "Friendly App Name",
	"description": "A short description shown in the catalogue.",
	"packageName": "com.example.app",
	"versionCode": 42
}
```

Only `name` and `description` affect catalogue text. `packageName` and `versionCode` are optional release metadata used when available. Without metadata, the catalogue falls back to the GitHub repository name and description.

## 🛠️ Development

```powershell
./gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Pushes to `master` build and publish a debug APK through `.github/workflows/release.yml`.

## 📜 License

No license file is currently included in this repository.