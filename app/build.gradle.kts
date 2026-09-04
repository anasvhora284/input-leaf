plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.inputleaf.android"
        minSdk = 26
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
    
    kotlinOptions { 
        jvmTarget = "17" 
    }
    
    buildFeatures { 
        compose = true
        aidl = true  // Enable AIDL for Shizuku IPC
    }

    sourceSets {
        getByName("main").assets.srcDir(
            project(":uhid-server").layout.buildDirectory.dir("generated/assets/uhid")
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
    
    // Custom APK naming
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abiName = output.getFilter(com.android.build.OutputFile.ABI) ?: "universal"
            val versionName = variant.versionName
            output.outputFileName = "input-leaf_${versionName}_${abiName}.apk"
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
