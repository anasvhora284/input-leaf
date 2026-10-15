# Releasing Input Leaf

The in-app updater compares version names by [SemVer](https://semver.org/) precedence,
so these rules are what decide who gets offered which build.

## Version numbers

- **Patch** (`1.4.4`): bug fixes and small additions.
- **Minor** (`1.5.0`): new user-facing features.
- `versionCode` goes up by one for **every published build**, pre-releases included.
  It is never reused, so a later build always installs over an earlier one and
  "What's new" shows on every update.

## Pre-release, then stable

1. **Pre-release:** set `versionName = "1.4.4-beta.1"` (then `beta.2`, … or `rc.1`) and the
   next `versionCode`. Tag `v1.4.4-beta.1` and publish it on GitHub as a **pre-release**.
2. **Stable:** set `versionName = "1.4.4"` and the next `versionCode`. Tag `v1.4.4` and
   publish it as **Latest**.

SemVer orders these `1.4.4-beta.1 < 1.4.4-beta.2 < 1.4.4-rc.1 < 1.4.4`, so:

| Installed | Channel | Offered |
|---|---|---|
| `1.4.3` | Stable | `1.4.4` once it is Latest; never a pre-release |
| `1.4.3` | Pre-release | `1.4.4-beta.1` as soon as it is published |
| `1.4.4-beta.1` | Pre-release (default for beta builds) | `1.4.4-beta.2`, then `1.4.4` |
| `1.4.4-beta.1` | Stable | `1.4.4` when it is Latest |

## Never do this

- Never give a pre-release the plain version name. A `1.4.3` pre-release followed by a
  `1.4.3` stable looks identical to the updater, so pre-release users are never told.
- Never move or reuse a published tag. Publish the next number instead.

## Checklist

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Add the version's entry to `RELEASES` in `UpdateService.kt` (keyed by the version
   without its `-beta.N` suffix).
3. Merge to `master`, tag the merge commit, build with `./gradlew :app:assembleRelease`.
4. Publish the GitHub release with the five APKs, marked pre-release or Latest as above.
