plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val brokerApplicationId = providers.gradleProperty("boundarylab.brokerApplicationId")
    .orElse("dev.boundarylab.broker").get()
val brokerBindPermission = providers.gradleProperty("boundarylab.brokerBindPermission")
    .orElse("dev.boundarylab.permission.BIND_BROKER").get()
val qualifiedName = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
require(qualifiedName.matches(brokerApplicationId)) { "boundarylab.brokerApplicationId must be fully qualified" }
require(qualifiedName.matches(brokerBindPermission)) { "boundarylab.brokerBindPermission must be fully qualified" }

android {
    namespace = "dev.boundarylab.broker"
    compileSdk = 36
    defaultConfig {
        applicationId = brokerApplicationId
        manifestPlaceholders["brokerBindPermission"] = brokerBindPermission
        buildConfigField("String", "BROKER_BIND_PERMISSION", "\"$brokerBindPermission\"")
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    implementation(project(":sdk:kotlin"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Platform fixture API is used only by instrumentation; never packaged in the broker.
    androidTestCompileOnly(files(androidComponents.sdkComponents.sdkDirectory.map {
        it.file("platforms/android-36/optional/android.test.mock.jar")
    }))
}
