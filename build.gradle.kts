plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
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
