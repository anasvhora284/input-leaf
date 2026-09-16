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
        variant("debugJvm") {
            xml {
                xmlFile = layout.buildDirectory.file("reports/kover/coverage-debug-jvm.xml").get().asFile
            }
        }
    }
}
