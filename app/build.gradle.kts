import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion = "3.0.1"
val komponenterVersjon = "1.0.52"
val tilgangVersjon = "0.0.30"
val behandlingsflytVersjon= "0.0.37"
val postmottakVersjon = "0.0.17"

plugins {
    id("oppgave.conventions")
    id("io.ktor.plugin") version "3.0.0"
}

application {
    mainClass.set("no.nav.aap.oppgave.server.AppKt")
}

dependencies {
    implementation("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbconnect:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbmigrering:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbtest:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:infrastructure:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:server:$komponenterVersjon")
    implementation("no.nav:ktor-openapi-generator:1.0.34")
    implementation("no.nav.aap.tilgang:api-kontrakt:$tilgangVersjon")
    implementation("no.nav.aap.behandlingsflyt:kontrakt:$behandlingsflytVersjon")
    implementation("no.nav.aap.postmottak:kontrakt:$postmottakVersjon")

    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.6")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    implementation(project(":dbflyway"))
    implementation(project(":api-kontrakt"))
    implementation("com.zaxxer:HikariCP:6.0.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.0")
    runtimeOnly("org.postgresql:postgresql:42.7.4")

    testImplementation("com.nimbusds:nimbus-jose-jwt:9.45")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.testcontainers:postgresql:1.20.1")
    constraints {
        implementation("org.apache.commons:commons-compress:1.27.1") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation(kotlin("test"))
}

tasks {
    withType<ShadowJar> {
        mergeServiceFiles()
    }
}