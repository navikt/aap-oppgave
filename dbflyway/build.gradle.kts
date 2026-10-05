plugins {
    id("aap.conventions")
}

dependencies {
    testImplementation(kelvinLibs.bundles.junit)
    testImplementation(kelvinLibs.junit.jupiter.engine)
}