import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

kover {
    currentProject {
        createVariant("debugJvm") {
            add("debug")
        }
    }
}

android {
    namespace = "com.inputleaf.android"
    compileSdk = 37
    
    defaultConfig {
        applicationId = "com.inputleaf.android"
        minSdk = 26
        // targetSdk deliberately stays on 34: 35+ enforces edge-to-edge, which is a
        // product decision, not a dependency bump.
        targetSdk = 34
        versionCode = 7
        versionName = "1.4.1"
        // JUnit4 runner so the androidTest classes are discovered on the emulator
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    signingConfigs {
        create("release") {
            storeFile = file("input-leaf.jks")
            storePassword = "inputleaf123"
            keyAlias = "input-leaf"
            keyPassword = "inputleaf123"
        }
    }
    
    buildTypes {
        debug {
            // Use project keystore so debug APKs can always update over each other
            // regardless of which machine built them
            signingConfig = signingConfigs.getByName("release")
            // JaCoCo-instrument debug APKs so connected Android tests feed the Codecov report
            isTestCoverageEnabled = true
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    // With built-in Kotlin there is no kotlinOptions block; the Kotlin jvmTarget
    // defaults to compileOptions.targetCompatibility above.
    
    buildFeatures { 
        compose = true
        aidl = true  // Enable AIDL for Shizuku IPC
    }

    sourceSets {
        // AGP 9 disallows providers here (android.sourceset.disallowProvider), so pass
        // the resolved directory; ordering against :uhid-server:buildDex is enforced by
        // the merge-assets dependsOn below.
        getByName("main").assets.srcDir(
            project(":uhid-server").layout.buildDirectory.dir("generated/assets/uhid").get().asFile
        )
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

// Custom APK naming: AGP 9 removed the legacy variant API (applicationVariants /
// BaseVariantOutputImpl) that used to rename outputs in place, and the public
// VariantOutput API does not expose outputFileName. Reproduce the historical
// input-leaf_<version>_<abi>.apk scheme with the public variant API instead: a Copy
// task that stages each variant's APKs under build/dist/<variant>/.
androidComponents {
    onVariants { variant ->
        val versionName = android.defaultConfig.versionName
            ?: error("versionName is required for the APK naming scheme")
        tasks.register<Copy>(
            "copy${variant.name.replaceFirstChar { it.uppercase() }}ApksToDist"
        ) {
            from(variant.artifacts.get(SingleArtifact.APK))
            into(layout.buildDirectory.dir("dist/${variant.name}"))
            rename { fileName ->
                Regex("app-([A-Za-z0-9_]+)-(debug|release)\\.apk").find(fileName)
                    ?.let { "input-leaf_${versionName}_${it.groupValues[1]}.apk" }
                    ?: fileName
            }
        }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(":uhid-server:buildDex")
}

tasks.matching { it.name.contains("lintVital", ignoreCase = true) }.configureEach {
    dependsOn(":uhid-server:buildDex")
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.datastore.preferences)
    implementation(libs.device.names)
    implementation(libs.coroutines.android)
    
    // Shizuku for privileged input injection without root
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.truth)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.truth)
}
