plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
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

application {
    mainClass.set("io.github.markovcd.capynib.cli.MainKt")
    applicationName = "capynib"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.serialization.json)
    implementation(libs.sqldelight.sqlite.driver)

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    usesService(gradle.sharedServices.registrations.getByName("liveMemosServer").service)
    environment("MEMOS_TEST_URL", System.getenv("MEMOS_TEST_URL") ?: "")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
