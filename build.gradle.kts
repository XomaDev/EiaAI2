plugins {
    id("java")
    kotlin("jvm")
}

group = "space.themelon.eia64"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(kotlin("stdlib-jdk8"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "space.themelon.eia64.Main")
    }
    archiveFileName.set("Eia64.jar")
    from({
        configurations.compileClasspath.get().filter {
            it.exists()
        }.map {
            if (it.isDirectory) it else project.zipTree(it)
        }
    })
    with(tasks.jar.get())
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.register("makeExtension") {
    dependsOn("fatJar")
    val d8Jar = rootProject.file("build-tools/d8.jar")

    val jarFile = "${layout.buildDirectory.get().asFile.absolutePath}/libs/Eia64.jar"
    val outputDir = rootProject.file("extension-skeleton/assets/")
    doLast {
        exec {
            commandLine(
                "java",
                "-cp",
                d8Jar.absolutePath,
                "com.android.tools.r8.D8",
                "--output",
                outputDir.absolutePath,
                jarFile
            )
        }
    }
    finalizedBy("zipStdlib")
}

tasks.register<Zip>("zipStdlib") {
    from(rootProject.file("stdlib"))
    archiveFileName.set("stdlib.zip")
    destinationDirectory.set(file("${rootProject.projectDir}/extension-skeleton/assets/"))

    finalizedBy(project(":extension").tasks.named("buildExtension"))
}

kotlin {
    jvmToolchain(11)
}