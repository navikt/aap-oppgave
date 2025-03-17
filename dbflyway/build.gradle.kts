plugins {
    id("oppgave.conventions")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.12.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.1")
    testImplementation("org.assertj:assertj-core:3.27.3")
}