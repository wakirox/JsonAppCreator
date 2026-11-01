import org.gradle.api.tasks.Delete

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("com.android.kotlin.multiplatform.library") version "9.5.0" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
