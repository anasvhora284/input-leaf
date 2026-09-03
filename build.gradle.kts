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
