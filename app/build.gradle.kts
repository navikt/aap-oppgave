import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion = "3.2.3"
val komponenterVersjon = "1.0.325"
val tilgangVersjon = "1.0.108"
val behandlingsflytVersjon = "0.0.410"
val postmottakVersjon = "0.0.111"

plugins {
    id("oppgave.conventions")
    id("io.ktor.plugin") version "3.2.3"
}

application {
    mainClass.set("no.nav.aap.oppgave.server.AppKt")
}

tasks.register<JavaExec>("genererOpenApiJson") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.oppgave.server.GenererOpenApiJsonKt")
}

dependencies {

    implementation(project(":dbflyway"))
    implementation(project(":api-kontrakt"))

    implementation("no.nav.aap.kelvin:httpklient:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbconnect:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:json:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbmigrering:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:dbtest:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:infrastructure:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:server:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:motor:$komponenterVersjon")
    implementation("no.nav.aap.kelvin:motor-api:$komponenterVersjon")
    implementation("no.nav:ktor-openapi-generator:1.0.120")
    implementation("no.nav.aap.tilgang:api-kontrakt:$tilgangVersjon")
    implementation("no.nav.aap.tilgang:plugin:$tilgangVersjon")
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
        implementation("io.netty:netty-common:4.2.3.Final")
    }
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    implementation("io.micrometer:micrometer-registry-prometheus:1.15.2")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    implementation("com.zaxxer:HikariCP:7.0.1")
    implementation("org.flywaydb:flyway-database-postgresql:11.11.1")
    implementation("io.getunleash:unleash-client-java:11.0.2")
    runtimeOnly("org.postgresql:postgresql:42.7.7")

    testImplementation("com.nimbusds:nimbus-jose-jwt:10.4.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation(kotlin("test"))
}

tasks {
    withType<ShadowJar> {
        mergeServiceFiles()
    }
}
