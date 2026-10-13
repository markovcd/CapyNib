// Every Gradle plugin is put on one classpath here, so the Kotlin plugin can see the Android
// plugin's classes; modules then apply them by id without a version.
buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        val kotlin = "2.4.21"
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlin")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlin")
        classpath("app.cash.sqldelight:gradle-plugin:2.4.1")
        if (providers.gradleProperty("capynib.jvmOnly").orNull != "true") {
            classpath("com.android.tools.build:gradle:9.4.0")
        }
    }
}
