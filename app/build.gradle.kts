plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.markovcd.capynib"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.markovcd.capynib"
        minSdk = 26
        targetSdk = 36
        // A release commit raises both; the tag v<versionName> marks it (docs/adr/0004).
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
        }
    }

    // The dependency block is encrypted with Google's key, which F-Droid cannot read or verify.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Robolectric needs the merged resources and manifest.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.environment("MEMOS_TEST_URL", System.getenv("MEMOS_TEST_URL") ?: "")
            it.systemProperty("capynib.versionCode", defaultConfig.versionCode.toString())
            val listing = rootProject.file("fastlane/metadata/android")
            it.inputs.dir(listing).withPropertyName("listing")
            it.systemProperty("capynib.listing", listing.path)
            // -Pcapynib.screenshots=true makes StoreScreenshotsTest write the listing's screenshots.
            if (providers.gradleProperty("capynib.screenshots").orNull == "true") {
                it.systemProperty("capynib.screenshots", listing.resolve("en-US/images/phoneScreenshots").path)
                it.outputs.upToDateWhen { false }
            }
        }
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.sqldelight.android.driver)
    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Canned memos replies for browser sign-in, through the client core uses.
    testImplementation(libs.okhttp)
}
