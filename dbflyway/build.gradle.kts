plugins {
    id("oppgave.conventions")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testImplementation("org.assertj:assertj-core:3.27.4")
}