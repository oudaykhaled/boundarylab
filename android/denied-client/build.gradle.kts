plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val probeStore = providers.environmentVariable("BOUNDARYLAB_DENIED_KEYSTORE")
val probePassword = providers.environmentVariable("BOUNDARYLAB_DENIED_PASSWORD")

android {
    namespace = "dev.boundarylab.deniedclient"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.boundarylab.deniedclient"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val independentSigning = if (probeStore.isPresent && probePassword.isPresent) {
        signingConfigs.create("denialProbe") {
            storeFile = file(probeStore.get())
            storePassword = probePassword.get()
            keyAlias = "denial-probe"
            keyPassword = probePassword.get()
        }
    } else null
    buildTypes.getByName("debug") { signingConfig = independentSigning }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

val validateDenialSigning by tasks.registering {
    doLast {
        check(probeStore.isPresent && probePassword.isPresent) {
            "Prepare separate signing via scripts/prepare-denied-signing.py and source its environment file."
        }
        val store = file(probeStore.get()).canonicalFile
        check(store.isFile && !store.toPath().startsWith(rootDir.canonicalFile.toPath())) {
            "Denial-probe keystore must exist outside the repository."
        }
    }
}
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(validateDenialSigning) }
dependencies {
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
