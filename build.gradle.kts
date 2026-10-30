plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "dev.boundarylab"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    tasks.withType<Test>().configureEach {
        maxParallelForks = 1
        maxHeapSize = "512m"
        reports.junitXml.required.set(true)
        reports.html.required.set(true)
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

tasks.register("checkJvm") {
    group = "verification"
    description = "Compile and run JVM tests; does not claim Android runtime execution."
    dependsOn(":core:check", ":desktop:host:check", ":desktop:cli:check")
}

tasks.register("checkAndroid") {
    group = "verification"
    description = "Build debug APKs, run local unit tests and Android lint; no device execution."
    dependsOn(":sdk:kotlin:testDebugUnitTest", ":sdk:kotlin:lintDebug",
        ":android:broker:assembleDebug", ":android:broker:testDebugUnitTest", ":android:broker:lintDebug",
        ":examples:android-native:assembleDebug", ":examples:android-native:testDebugUnitTest", ":examples:android-native:lintDebug")
}

tasks.named("check") { dependsOn("checkJvm", "checkAndroid") }
