import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "no.nav.aap.oppgave"
version = project.findProperty("version")?.toString() ?: "0.0.0"

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/navikt/behandlingsflyt")
        credentials {
            username = "x-access-token"
            password = (project.findProperty("githubPassword")
                ?: System.getenv("GITHUB_PASSWORD")
                ?: System.getenv("GITHUB_TOKEN")
                ?: error("")).toString()
        }
    }
}

testing {
    suites {
        @Suppress("UnstableApiUsage") val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}


kotlin.sourceSets["main"].kotlin.srcDirs("main")
kotlin.sourceSets["test"].kotlin.srcDirs("test")
sourceSets["main"].resources.srcDirs("main")
sourceSets["test"].resources.srcDirs("test")