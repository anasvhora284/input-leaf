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
                    "com.inputleaf.android.shizuku",
                )
                classes(
                    "com.inputleaf.android.InputLeafApplication*",
                    "com.inputleaf.android.inject.AccessibilityInputService*",
                    "com.inputleaf.android.inject.InputLeafIME*",
                    "com.inputleaf.android.inject.AccessibilityInputInjector*",
                    "com.inputleaf.android.inject.KeysymInjection*",
                    "com.inputleaf.android.storage.ClientCertificateStore*",
                    // ConnectionService, CursorOverlayService and NotificationHelper remain
                    // excluded: they are Android framework adapters whose Service/Settings/IME/
                    // overlay/notification effects the JVM cannot exercise. The connected
                    // android-coverage job reports ConnectionService from the emulator instead.
                    // ConnectionCoordinator and AppPreferences are plain JVM logic with dedicated
                    // unit tests and must report.
                    "com.inputleaf.android.service.ConnectionService*",
                    "com.inputleaf.android.service.CursorOverlayService*",
                    "com.inputleaf.android.service.NotificationHelper*",
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
