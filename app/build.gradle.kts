import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("aap.conventions")
    id("io.ktor.plugin") version "3.4.0"
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

    implementation(libs.ktorServerAuth)
    implementation(libs.ktorServerAuthJwt)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorServerCallLogging)
    implementation(libs.ktorServerCallId)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorServerMetricsMicrometer)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktorSerializationJackson)

    implementation(libs.jacksonDatabind)
    implementation(libs.jacksonDatatypeJsr310)
    implementation(libs.micrometerRegistryPrometheus)
    implementation(libs.logbackClassic)
    implementation(libs.logstashLogbackEncoder)

    implementation(libs.hikariCp)
    implementation(libs.caffeine)
    implementation(libs.flywayDatabasePostgresql)
    implementation(libs.unleashClientJava)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.nimbusJoseJwt)
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testImplementation(libs.testcontainersJunitJupiter)
    testImplementation(libs.assertjCore)
    testImplementation(libs.testcontainersPostgresql)
    constraints {
        implementation(libs.commonsCompress) {
            because("https://github.com/advisories/GHSA-4g9r-vxhx-9pgx")
        }
    }
    testImplementation(kotlin("test"))
}

tasks {
    withType<ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }
}
