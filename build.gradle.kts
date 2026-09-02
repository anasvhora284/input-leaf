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
        filters {
            excludes {
                packages(
                    "com.inputleaf.android.ui",
                    "com.inputleaf.android.ui.components",
                    "com.inputleaf.android.ui.theme",
                    "com.inputleaf.android.service",
                    "com.inputleaf.android.shizuku",
                )
                classes(
                    "com.inputleaf.android.InputLeafApplication*",
                    "com.inputleaf.android.inject.AccessibilityInputService*",
                    "com.inputleaf.android.inject.InputLeafIME*",
                    "com.inputleaf.android.inject.AccessibilityInputInjector*",
                    "com.inputleaf.android.inject.KeysymInjection*",
                    "com.inputleaf.android.storage.ClientCertificateStore*",
                    "com.inputleaf.android.storage.AppPreferences*",
                )
            }
        }
        variant("debugJvm") {
            xml {
                xmlFile = layout.buildDirectory.file("reports/kover/coverage-debug-jvm.xml").get().asFile
            }
        }
    }
}
