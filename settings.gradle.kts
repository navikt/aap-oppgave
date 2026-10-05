rootProject.name = "oppgave"

include(
    "app",
    "dbflyway",
    "api-kontrakt"
)


dependencyResolutionManagement {
    // Felles for alle gradle prosjekter i repoet
    versionCatalogs {
        create("kelvinLibs") {
            from("no.nav.aap.kelvin:version-catalog:2.0.177")
        }
    }
    @Suppress("UnstableApiUsage")
    repositories {
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        mavenCentral()
        mavenLocal()
    }
}
