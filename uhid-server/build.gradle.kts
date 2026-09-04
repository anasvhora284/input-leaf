import java.io.File
import java.util.Properties

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
    compileOnly(libs.android.stub)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
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

    fun resolveSdkDir(): String {
        val env = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
        if (!env.isNullOrBlank()) return env
        val localProps = rootProject.file("local.properties")
        if (localProps.exists()) {
            val properties = Properties()
            localProps.inputStream().use { properties.load(it) }
            val dir = properties.getProperty("sdk.dir")
            if (!dir.isNullOrBlank()) return dir
        }
        return ""
    }

    val sdkRoot = resolveSdkDir()
    val d8Path = if (sdkRoot.isNotBlank()) "$sdkRoot/build-tools/36.0.0/d8" else ""
    val androidJar = if (sdkRoot.isNotBlank()) "$sdkRoot/platforms/android-36/android.jar" else ""

    inputs.file(jarPath)
    outputs.file(dexFile)

    doFirst {
        require(sdkRoot.isNotBlank()) { "ANDROID_SDK_ROOT, ANDROID_HOME, or sdk.dir in local.properties is required to build the UHID DEX" }
        require(File(d8Path).exists()) { "Android build tools 36.0.0 are required to build the UHID DEX (checked $d8Path)" }
        require(File(androidJar).exists()) { "Android platform 36 is required to build the UHID DEX (checked $androidJar)" }
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

