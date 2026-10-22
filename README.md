# AppExtractor

A native Android APK export utility, built with Kotlin and Jetpack Compose.

## Features
- Browse user-installed apps in a searchable grid.
- Hide system apps by default; reveal them in Settings only when their APK files are readable.
- Multi-select apps, export base APKs and split APK parts, and save them under `Download/App Extractor`.
- Use Android's system uninstall confirmation.
- Separate APK file manager, Settings, and About screens.
- Arabic/English language toggle and dark/light theme preferences.
- No Internet permission; app lists and exported APK files stay on device.

## Build
GitHub Actions builds the debug APK. Open the Actions tab and download the `AppExtractor-debug-apk` artifact from a successful workflow run.

## Security notes
This app is intended for personal sideloading/testing. It requests package visibility to list apps. It does not bypass Android's package installer, Play Protect, or uninstall confirmations. A debug build may still show an Android/Play Protect warning because it is sideloaded and debug-signed; that warning cannot safely be disabled by app code.
