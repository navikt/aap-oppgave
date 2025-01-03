import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion = "3.0.3"
val komponenterVersjon = "1.0.101"
val tilgangVersjon = "0.0.72"
val behandlingsflytVersjon = "0.0.87"
val postmottakVersjon = "0.0.24"

plugins {
    id("oppgave.conventions")
    id("io.ktor.plugin") version "3.0.2"
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
    implementation("no.nav.aap.kelvin:motor:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:motor-api:$komponenterVersjon")
    implementation("no.nav:ktor-openapi-generator:1.0.66")
    implementation("no.nav.aap.tilgang:api-kontrakt:$tilgangVersjon")
    implementation("no.nav.aap.behandlingsflyt:kontrakt:$behandlingsflytVersjon")
    implementation("no.nav.aap.postmottak:kontrakt:$postmottakVersjon")

    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    constraints {
        implementation("io.netty:netty-common:4.1.116.Final")
    }

    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.0")
    implementation("ch.qos.logback:logback-classic:1.5.15")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    implementation(project(":dbflyway"))
    implementation(project(":api-kontrakt"))
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.flywaydb:flyway-database-postgresql:11.1.0")
    runtimeOnly("org.postgresql:postgresql:42.7.4")

    testImplementation("com.nimbusds:nimbus-jose-jwt:9.47")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("org.testcontainers:postgresql:1.20.4")
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