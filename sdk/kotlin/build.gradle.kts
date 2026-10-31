plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
    id("org.jetbrains.dokka")
}
android {
    namespace = "dev.boundarylab.sdk"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    publishing { singleVariant("release") { withSourcesJar() } }
}
dependencies { api(project(":core")) }

publishing {
    publications.create<MavenPublication>("library") {
        artifactId = "boundarylab-android"
        pom {
            name.set("BoundaryLab Android SDK")
            description.set("Same-publisher authenticated Android Binder client for BoundaryLab, API 30+.")
        }
        afterEvaluate { from(components["release"]) }
    }
}
apply(from = rootProject.file("gradle/library-publication.gradle.kts"))
