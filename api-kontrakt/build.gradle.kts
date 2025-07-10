plugins {
    id("oppgave.conventions")
    `maven-publish`
    `java-library`
}

group = "no.nav.aap.oppgave"

apply(plugin = "maven-publish")
apply(plugin = "java-library")

val jacksonVersion = "2.19.1"

dependencies {
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    api("no.nav:ktor-openapi-generator:1.0.117")
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name
            version = project.findProperty("version")?.toString() ?: "0.0.0"
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/navikt/aap-oppgave")
            credentials {
                username = "x-access-token"
                // Ligger tilgjengelig i Github Actions
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
