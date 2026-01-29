plugins {
    id("aap.conventions")
}

dependencies {
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testImplementation(libs.assertjCore)
}