plugins {
    id("oppgave.conventions")
}

dependencies {
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testImplementation(libs.assertjCore)
}