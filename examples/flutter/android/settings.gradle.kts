pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.library") version "8.11.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
}

include(":app")

dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../../../gradle/libs.versions.toml")) } }
}
include(":sdk:kotlin", ":core")
project(":sdk:kotlin").projectDir = file("../../../sdk/kotlin")
project(":core").projectDir = file("../../../core")
