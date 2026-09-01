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
        variant("debugJvm") {
            xml {
                xmlFile = layout.buildDirectory.file("reports/kover/coverage-debug-jvm.xml").get().asFile
            }
        }
    }
}
