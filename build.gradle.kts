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
    compileOnly(fileTree("libai2") {
        include("*.jar")
    })
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
            // we do not need to include libai2!
            it.exists() && !it.path.contains("libai2")
        }.map {
            if (it.isDirectory) it else project.zipTree(it)
        }
    })
    with(tasks.jar.get())
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.register("makeExtension") {
    dependsOn("fatJar")
    // optimize the Eia64.jar using ProGuard config
    val proguardJar = rootProject.file("build-tools/proguard-7.6.0.jar")
    val proguardRules = rootProject.file("proguard-rules.pro")
    val inputJar = layout.buildDirectory.file("libs/Eia64.jar").get().asFile
    val outputJar = layout.buildDirectory.file("libs/Eia64-optimized.jar").get().asFile

    doLast {
        exec {
            commandLine(
                "java",
                "-jar",
                proguardJar.absolutePath,
                "-injars",
                inputJar.absolutePath,
                "-outjars",
                outputJar.absolutePath,
                "-libraryjars",
                "${System.getenv("JAVA_HOME") ?: System.getProperty("java.home")}/jmods/java.base.jmod",
                "-include",
                proguardRules.absolutePath
            )
        }
    }

    finalizedBy("d8Eia")
}

tasks.register("d8Eia") {
    val d8Jar = rootProject.file("build-tools/d8.jar")

    val jarFile = "${layout.buildDirectory.get().asFile.absolutePath}/libs/Eia64-optimized.jar"
    val outputFile = rootProject.file("ext-skeleton/assets/eia.jar")
    doLast {
        exec {
            commandLine(
                "java",
                "-cp",
                d8Jar.absolutePath,
                "com.android.tools.r8.D8",
                "--output",
                outputFile.absolutePath,
                jarFile
            )
        }
    }
    finalizedBy(project(":ext-src").tasks.named("buildExtension"))
}

kotlin {
    jvmToolchain(11)
}