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
        targetSdk = 34
        versionCode = 8
        versionName = "1.4.2"
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
            enableAndroidTestCoverage = true
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
    
    buildFeatures { 
        compose = true
        aidl = true  // Enable AIDL for Shizuku IPC
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

androidComponents {
    onVariants { variant ->
        val capitalizedName = variant.name.replaceFirstChar { it.uppercase() }
        val versionName = android.defaultConfig.versionName
            ?: error("versionName is required for the APK naming scheme")
        val copyDist = tasks.register<Copy>("copy${capitalizedName}ApksToDist") {
            from(variant.artifacts.get(SingleArtifact.APK))
            into(layout.buildDirectory.dir("dist/${variant.name}"))
            rename { fileName ->
                Regex("app-([A-Za-z0-9_-]+)-(debug|release)\\.apk").find(fileName)
                    ?.let { "input-leaf_${versionName}_${it.groupValues[1]}.apk" }
                    ?: fileName
            }
        }
        tasks.matching { it.name == "assemble$capitalizedName" }.configureEach {
            dependsOn(copyDist)
        }
    }
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
    testImplementation(libs.androidx.test.core)
    // Real org.json for JVM unit tests (Android's JSONObject stub is not mocked)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Forces the Android 14+ compatible Espresso over the 3.5.0 the Compose BOM drags in.
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.truth)
}
