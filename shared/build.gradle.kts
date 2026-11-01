// The engine of Appkin without its UI: the model, parsing and validation, the runtime state and use cases, and the
// editor and AI logic. Android uses it from :app; an iPhone app would link the same code as a framework.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    // A compiler warning fails the build, on every target (the app's too).
    compilerOptions {
        allWarningsAsErrors = true
    }
    android {
        namespace = "it.sapienza.appinterpreter.shared"
        compileSdk = 37
        minSdk = 33
        withHostTestBuilder {}
    }
    // Declared so common code stays iOS-ready; the framework can only be linked on a Mac.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "AppkinShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.12.0")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            // JSON Schema draft 2020-12 in common code, on kotlinx.serialization (replaces networknt + Jackson).
            implementation("io.github.optimumcode:json-schema-validator:0.5.5")
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            api("io.ktor:ktor-client-core:3.6.0")
            // The screens' state: multiplatform androidx ViewModels with SavedStateHandle, exposing StateFlows.
            api("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
            api("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")
        }
        androidMain.dependencies {
            // Ktor runs on the app's OkHttp client, which Coil shares for images.
            api("io.ktor:ktor-client-okhttp:3.6.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.6.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.12.0")
            implementation("io.ktor:ktor-client-mock:3.6.0")
        }
    }
}

/** A Kotlin string literal holding [text] exactly. */
fun kotlinString(text: String): String = buildString {
    append('"')
    text.forEach { c ->
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
    append('"')
}

// The v3 schema as a constant, so every platform validates with the repository's one copy of it.
val generateSchemaSource by tasks.registering {
    val schema = rootProject.file("schema/v3.schema.json")
    val output = layout.buildDirectory.dir("generated/schema/kotlin")
    inputs.file(schema)
    outputs.dir(output)
    doLast {
        val file = output.get().file("it/sapienza/appinterpreter/validation/V3Schema.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package it.sapienza.appinterpreter.validation\n\n" +
                "/** schema/v3.schema.json, copied in at build time. */\n" +
                "object V3Schema {\n    const val JSON: String = ${kotlinString(schema.readText())}\n}\n"
        )
    }
}

// The bundled configurations, for the common tests (which can't read files on every platform).
val generateTestFixtures by tasks.registering {
    val directories = listOf("examples", "samples", "app/src/main/res/raw").map { rootProject.file(it) }
    val output = layout.buildDirectory.dir("generated/fixtures/kotlin")
    directories.forEach { inputs.dir(it) }
    outputs.dir(output)
    doLast {
        val entries = directories.flatMap { directory ->
            directory.listFiles { file -> file.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
                .map { "${directory.name}/${it.name}" to it.readText() }
        }
        val file = output.get().file("it/sapienza/appinterpreter/Fixtures.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package it.sapienza.appinterpreter\n\n" +
                "/** Every bundled configuration (examples/, samples/ and the app's raw resources) " +
                "by \"folder/name.json\". */\n" +
                "object Fixtures {\n    val configurations: Map<String, String> = mapOf(\n" +
                entries.joinToString(",\n") { (name, text) ->
                    "        ${kotlinString(name)} to ${kotlinString(text)}"
                } +
                "\n    )\n}\n"
        )
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir(generateSchemaSource) }
kotlin.sourceSets.commonTest { kotlin.srcDir(generateTestFixtures) }

// Kotlin style: ktlint (its rules and .editorconfig); any finding fails the build.
ktlint {
    version.set("1.8.0")
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}
