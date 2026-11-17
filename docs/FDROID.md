# F-Droid

F-Droid builds Input Leaf from source and signs it with its own key, so an F-Droid install and a
GitHub install can't update each other (Android refuses a different signature). The app detects an
F-Droid install (`InstallSource.FDROID`) and never contacts GitHub there; updates come through F-Droid.

## What lives in this repo
- `fastlane/metadata/android/en-US/`: title, descriptions, icon, feature graphic, screenshots, and
  `changelogs/<versionCode>.txt` (max 500 bytes). **Add `changelogs/<new versionCode>.txt` for every release.**
- `app/build.gradle.kts`: signs only when `app/input-leaf.jks` exists, drops Google's dependency-info
  blob, and builds one universal APK with `-PnoAbiSplits`.
- `docs/fdroid/com.inputleaf.android.yml`: the recipe for F-Droid's `fdroiddata` repository.

## Submitting (once)
1. Fork https://gitlab.com/fdroid/fdroiddata and copy the recipe to `metadata/com.inputleaf.android.yml`.
2. Open a merge request ("New app: Input Leaf"). F-Droid's CI runs `fdroid lint`, `fdroid build` and the source scanner.
3. After it merges, `UpdateCheckMode: Tags` picks up every `vX.Y.Z` tag on its own (betas are ignored).

## Checking a build locally
```
mv app/input-leaf.jks /tmp/            # F-Droid has no key
./gradlew :app:assembleRelease -PnoAbiSplits
# -> app/build/outputs/apk/release/app-release-unsigned.apk
mv /tmp/input-leaf.jks app/
```
