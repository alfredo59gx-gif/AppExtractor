# AppExtractor

A native Android APK export utility built with Kotlin and Jetpack Compose.

## Features
- Searchable grid of exportable installed apps.
- System apps hidden by default; optional display from Settings if their APK files are readable.
- Multi-select, base APK and split APK export to `Download/App Extractor`.
- Android system uninstall confirmation.
- Separate APK file manager, app details, settings, and about screens.
- Arabic/English language choice and dark/light theme.
- No Internet permission; app list and exported APK files stay on device.

## Build
GitHub Actions produces an optimized release variant artifact named `AppExtractor-optimized-apk`. Download it from a successful run in the Actions tab. The APK is signed with the standard Android debug key for personal testing.

## Security notes
This utility is intended for personal sideloading/testing. It needs package visibility to list installed apps and requests package uninstall through Android's system interface. It does not bypass the package installer or Play Protect. Because it is sideloaded and signed with a debug key, Android or Play Protect may still show an installation warning; app code cannot safely suppress those protections. For public distribution, use a private release keystore you control.
