plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlinx.kover")
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
        versionCode = 6
        versionName = "1.4.0"
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.jaredrummler:android-device-names:2.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Shizuku for privileged input injection without root
    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")
    
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.truth:truth:1.4.5")
}
