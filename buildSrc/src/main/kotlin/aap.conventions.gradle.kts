// Felles kode for alle build.gradle.kts filer som laster inn denne conventions pluginen

plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "no.nav.aap.oppgave"
version = project.findProperty("version")?.toString() ?: "0.0.0"

// https://docs.gradle.org/8.12.1/userguide/jvm_test_suite_plugin.html
testing {
    suites {
        @Suppress("UnstableApiUsage") val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
        maxParallelForks = Runtime.getRuntime().availableProcessors()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    (findByName("jar") as? Jar)?.apply {
        // Bruk et unikt navn for jar-filen til hver submodul, for å unngå navnekollisjoner i multi-modul prosjekt,
        // gjennom at vi ikke bruker samme navn, feks. "kontrakt.jar" og "api.jar", i flere moduler.
        // Dette unngår feil av typen "Entry <name>.jar is a duplicate but no duplicate handling strategy has been set"
        // Alternativet er å unngå å bruke det eksakt samme navnet på submoduler fra forskjellige moduler,
        // som feks "kontrakt".
        archiveBaseName.set("${rootProject.name}-${project.name}")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)

        // Bruk et unikt navn for <submodule>.kotlin_module for hver Gradle-submodul, for å unngå navnekollisjoner i
        // multi-modul prosjekt, hvor vi inkluderer flere av våre kotlin-moduler i samme jar-fil eller
        // på samme runtime classpath. Kroneksempelet er "kontrakt.kotlin_module" fra både behandlingsflyt, brev,
        // meldekort og andre steder.
        // Dette gjør at vi kan beholde informasjonen for hver kotlin_module, og kotlin-reflect og andre verktøy
        // fungerer som forventet. Alternativet er å unngå å bruke det eksakt samme navnet på submoduler fra
        // forskjellige moduler, som feks "kontrakt".
        moduleName.set("${rootProject.name}-${project.name}")
    }
}

// Pass på at når vi kaller JavaExec eller Test tasks så bruker vi samme språk-versjon som vi kompilerer til
val toolchainLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.withType<Test>().configureEach { javaLauncher.set(toolchainLauncher) }
tasks.withType<JavaExec>().configureEach { javaLauncher.set(toolchainLauncher) }


kotlin.sourceSets["main"].kotlin.srcDirs("main")
kotlin.sourceSets["test"].kotlin.srcDirs("test")
sourceSets["main"].resources.srcDirs("main")
sourceSets["test"].resources.srcDirs("test")