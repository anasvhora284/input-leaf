// AGP 9 compiles Kotlin itself (built-in Kotlin) with a bundled Kotlin Gradle plugin
// version. Placing a newer KGP on the buildscript classpath upgrades the built-in
// compiler to that version; this is the documented override mechanism:
// https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin
// Catalog accessors cannot be used inside the buildscript block, so the version is
// read straight from gradle/libs.versions.toml — the catalog stays the single source
// of truth, and a renamed or missing entry fails the build right here.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        val kotlinVersion = Regex("(?m)^kotlin\\s*=\\s*\"([^\"]+)\"")
            .find(rootDir.resolve("gradle/libs.versions.toml").readText())
            ?.groupValues?.get(1)
            ?: error("kotlin version entry not found in gradle/libs.versions.toml")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":app"))
}

kover {
    currentProject {
        createVariant("debugJvm") {
        }
    }

    reports {
        // No coverage exclusions: every class reports truthfully. The jvm session (this
        // report) covers plain JVM logic; the android-coverage emulator session covers the
        // framework adapters; Codecov merges both line-by-line, so the enforced 100% patch
        // gate needs no package/class allow-lists and untested code stays visible.
        variant("debugJvm") {
            xml {
                xmlFile = layout.buildDirectory.file("reports/kover/coverage-debug-jvm.xml").get().asFile
            }
        }
    }
}
