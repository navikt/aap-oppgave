import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap.conventions")
    alias(kelvinLibs.plugins.ktor)
}

application {
    mainClass.set("no.nav.aap.oppgave.server.AppKt")
}

tasks.register<JavaExec>("genererOpenApiJson") {
    description = "Generer openapi.json og lagre den."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.oppgave.server.GenererOpenApiJsonKt")
}

tasks.register<JavaExec>("runTestApp") {
    description = "Kjør TestApp mot lokal behandlingsflyt"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.oppgave.server.TestAppKt")
    environment(
        "NAIS_CLUSTER_NAME" to "LOCAL",
        "NAIS_DATABASE_OPPGAVE_OPPGAVE_JDBC_URL" to "jdbc:postgresql://localhost:5439/postgres",
        "NAIS_DATABASE_OPPGAVE_OPPGAVE_USERNAME" to "postgres",
        "NAIS_DATABASE_OPPGAVE_OPPGAVE_PASSWORD" to "",
        "INTEGRASJON_BEHANDLINGSFLYT_URL" to "http://localhost:8080",
        "LOKAL_BEHANDLINGSFLYT_AZURE_PORT" to "8081",
    )
}

tasks.register<JavaExec>("runTestAppMotBehandlingsflyt") {
    description = "Kjør TestApp mot lokal behandlingsflyt (samme config som .run/TestApp (mot behandlingsflyt).run.xml)"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("no.nav.aap.oppgave.server.TestAppKt")
    environment(
        "NAIS_CLUSTER_NAME" to "LOCAL",
        "NAIS_DATABASE_OPPGAVE_OPPGAVE_JDBC_URL" to "jdbc:postgresql://localhost:5439/postgres",
        "NAIS_DATABASE_OPPGAVE_OPPGAVE_USERNAME" to "postgres",
        "NAIS_DATABASE_OPPGAVE_OPPGAVE_PASSWORD" to "",
        "INTEGRASJON_BEHANDLINGSFLYT_URL" to "http://localhost:8080",
        "LOKAL_BEHANDLINGSFLYT_AZURE_PORT" to "8081",
        "HTTP_PORT" to "8084",
    )
}

dependencies {
    implementation(project(":dbflyway"))
    implementation(project(":api-kontrakt"))

    implementation(libs.httpklient)
    implementation(libs.dbconnect)
    implementation(libs.json)
    implementation(libs.dbmigrering)
    implementation(libs.dbtest)
    implementation(libs.infrastructure)
    implementation(libs.server)
    implementation(libs.motor)
    implementation(libs.motorApi)
    implementation(libs.ktorOpenapiGenerator)
    implementation(libs.tilgangKontrakt)
    implementation(libs.tilgangPlugin)
    implementation(libs.behandlingsflytKontrakt)
    implementation(libs.postmottakKontrakt)

    implementation(kelvinLibs.ktor.server.auth)
    implementation(kelvinLibs.ktor.server.auth.jwt)
    implementation(kelvinLibs.ktor.client.cio)
    implementation(kelvinLibs.ktor.client.content.negotiation)
    implementation(kelvinLibs.ktor.server.call.logging)
    implementation(kelvinLibs.ktor.server.call.id)
    implementation(kelvinLibs.ktor.server.content.negotiation)
    implementation(kelvinLibs.ktor.server.metrics.micrometer)
    implementation(kelvinLibs.ktor.server.netty)
    implementation(kelvinLibs.ktor.server.status.pages)
    implementation(kelvinLibs.ktor.serialization.jackson)

    implementation(kelvinLibs.jackson.databind)
    implementation(kelvinLibs.jackson.datatype.jsr310)
    implementation(kelvinLibs.micrometer.prometheus)
    implementation(kelvinLibs.logback.classic)
    implementation(kelvinLibs.logstash.logback.encoder)

    implementation(kelvinLibs.hikaricp)
    implementation(kelvinLibs.caffeine)
    implementation(kelvinLibs.flyway.postgresql)
    implementation(kelvinLibs.unleash.client.java)
    runtimeOnly(kelvinLibs.postgresql)

    testImplementation(kelvinLibs.nimbus.jose.jwt)
    testImplementation(kelvinLibs.bundles.junit)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${kelvinLibs.versions.testcontainers.get()}")
    testImplementation(kelvinLibs.testcontainers.postgresql)
    testImplementation(kotlin("test"))
}

tasks {
    withType<ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }
}
