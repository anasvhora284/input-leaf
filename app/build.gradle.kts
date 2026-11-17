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
        versionCode = 16
        versionName = "1.4.6"
        // JUnit4 runner so the androidTest classes are discovered on the emulator
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    // The keystore is gitignored. Without it (F-Droid signs its own builds) the APKs are
    // left unsigned instead of failing the build.
    val projectKeystore = file("input-leaf.jks")
    signingConfigs {
        if (projectKeystore.exists()) {
            create("release") {
                storeFile = projectKeystore
                storePassword = "inputleaf123"
                keyAlias = "input-leaf"
                keyPassword = "inputleaf123"
            }
        }
    }
    
    buildTypes {
        debug {
            // Use project keystore so debug APKs can always update over each other
            // regardless of which machine built them
            signingConfig = signingConfigs.findByName("release")
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
            // Stripping depends on the build machine's NDK; unstripped libraries make the
            // APK reproducible, so F-Droid can publish our signed build.
            keepDebugSymbols += "**/*.so"
        }
    }
    
    // Google's encrypted dependency list in the APK is not open source, and F-Droid rejects it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    splits {
        abi {
            // -PnoAbiSplits builds the single universal APK, which F-Droid needs (one APK per versionCode).
            isEnable = !project.hasProperty("noAbiSplits")
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
    implementation(libs.coroutines.android)
    
    // Shizuku for privileged input injection without root
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // libsu RootService: same IInputInjector AIDL as Shizuku, running as uid 0
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    
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
