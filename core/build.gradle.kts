plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
    `java-test-fixtures`
}

// Bytecode the Android app can consume; any JDK from 17 up builds it.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors = true
    }
}

sqldelight {
    databases {
        create("CapynibDatabase") {
            packageName.set("io.github.markovcd.capynib.core.db")
            // Each released schema is kept here as N.db; migrations are verified against them.
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.sqldelight.coroutines)

    // Doubles and helpers other modules' tests use too: a fake and a live memos server, temp stores.
    testFixturesApi(libs.sqldelight.sqlite.driver)
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.okhttp)
    testFixturesImplementation(libs.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    usesService(gradle.sharedServices.registrations.getByName("liveMemosServer").service)
    // The memos integration tests read these; the gate always sets them.
    environment("MEMOS_TEST_URL", System.getenv("MEMOS_TEST_URL") ?: "")
    environment("MEMOS_TEST_REACH_BACK", System.getenv("MEMOS_TEST_REACH_BACK") ?: "")
    // Environment is no task input, but which server the live tests ran against is: the gate names
    // each memos server by its version, so a result is reused only for the same release.
    inputs.property("memosTestUrl", System.getenv("MEMOS_TEST_URL") ?: "")
    // Tests read these repository files, so a change to them reruns the tests.
    inputs.file(rootProject.file("memos-versions.txt")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file(".github/workflows")).withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The coroutines test API (virtual time, test dispatchers) is still marked experimental.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
}
