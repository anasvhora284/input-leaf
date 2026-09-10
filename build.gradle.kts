plugins {
    id("com.android.application") version "8.7.0" apply false
    id("com.android.library") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
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
