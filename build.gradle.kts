// Kotlin konfigurasjonen er gitt av pluginen 'aap.conventions' i buildSrc
// og settings.gradle.kts

plugins {
    // Provides a no-op 'build' lifecycle task
    base
    id("aap.conventions")
}


// Call the tasks of the subprojects
for (taskName in listOf<String>("clean", "build", "check")) {
    tasks.named(taskName) {
        dependsOn(subprojects.map { it.path + ":$taskName" })
    }
}

