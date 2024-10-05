plugins {
    id("java")
    kotlin("jvm")
}

group = "space.themelon.eiaai2"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    compileOnly(rootProject.fileTree("libai2") { include("*.jar") })
}

tasks.register<Jar>("buildExtension") {
    archiveClassifier.set("all")
    archiveFileName.set("AndroidRuntime.jar")
    with(tasks.jar.get())
    destinationDirectory.set(rootProject.file("extension-skeleton/files/"))
    duplicatesStrategy = DuplicatesStrategy.WARN

    // now we gotta dex em
    finalizedBy("d8")
}

tasks.register("d8") {
    val d8Jar = rootProject.file("build-tools/d8.jar")

    val jarFile = rootProject.file("extension-skeleton/files/AndroidRuntime.jar")
    val outputDir = rootProject.file("extension-skeleton/")

    doLast {
        exec {
            commandLine(
                "java",
                "-cp",
                d8Jar.absolutePath,
                "com.android.tools.r8.D8",
                "--output",
                outputDir.absolutePath,
                jarFile.absolutePath
            )
        }
    }
    finalizedBy("zipExtension")
}

tasks.register<Zip> ("zipExtension") {
    from(rootProject.file("extension-skeleton/")) {
        into("space.themelon.eiaai2")
    }
    archiveFileName.set("space.themelon.eiaai2.aix")
    destinationDirectory.set(rootProject.file("${layout.buildDirectory.get().asFile.absolutePath}/"))
}

tasks.test {
    useJUnitPlatform()
}