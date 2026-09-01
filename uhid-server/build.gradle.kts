plugins {
    id("java")
    id("jacoco")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    // Android stub for android.net.LocalServerSocket / LocalSocket (compile-time only)
    compileOnly("com.google.android:android:4.1.1.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
}
// Step 1: fat JAR
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("inputleaf-uhid")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "com.inputleaf.uhid.Main" }
    from(sourceSets.main.get().output)
}
// Step 2: convert to DEX in build output; the app consumes this as a generated asset.
tasks.register<Exec>("buildDex") {
    dependsOn("fatJar")
    val jarPath = layout.buildDirectory.file("libs/inputleaf-uhid.jar").get().asFile
    val dexOut = layout.buildDirectory.dir("generated/assets/uhid").get().asFile
    val dexFile = dexOut.resolve("classes.dex")
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME") ?: ""
    val d8Path = "$sdkRoot/build-tools/34.0.0/d8"
    val androidJar = "$sdkRoot/platforms/android-34/android.jar"

    inputs.file(jarPath)
    outputs.file(dexFile)

    doFirst {
        require(sdkRoot.isNotBlank()) { "ANDROID_SDK_ROOT or ANDROID_HOME is required to build the UHID DEX" }
        require(File(d8Path).exists()) { "Android build tools 34.0.0 are required to build the UHID DEX" }
        require(File(androidJar).exists()) { "Android platform 34 is required to build the UHID DEX" }
        project.delete(dexOut)
        dexOut.mkdirs()
    }
    commandLine(
        d8Path,
        "--lib", androidJar,
        "--min-api", "26",
        "--output", dexOut.absolutePath,
        jarPath.absolutePath
    )
}
