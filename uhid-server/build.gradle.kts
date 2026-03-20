plugins { id("java") }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    // Android stub for android.net.LocalServerSocket / LocalSocket (compile-time only)
    compileOnly("com.google.android:android:4.1.1.4")
}
// Step 1: fat JAR
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("inputleaf-uhid")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "com.inputleaf.uhid.Main" }
    from(sourceSets.main.get().output)
}
// Step 2: convert to DEX and copy to app assets
tasks.register<Exec>("buildDex") {
    dependsOn("fatJar")
    val jarPath = layout.buildDirectory.file("libs/inputleaf-uhid.jar").get().asFile
    val dexOut  = project(":app").layout.projectDirectory.file("src/main/assets").asFile
    val d8Path = "${System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME") ?: ""}/build-tools/34.0.0/d8"
    doFirst { dexOut.mkdirs() }
    commandLine(if (File(d8Path).exists()) d8Path else "d8", "--output", dexOut.absolutePath, jarPath.absolutePath)
}
