plugins {
    id("com.android.application") version "8.11.1"
    kotlin("android") version "2.2.21"
}

val brokerPackage = providers.gradleProperty("boundarylab.brokerApplicationId").getOrElse("dev.boundarylab.broker")
val brokerService = providers.gradleProperty("boundarylab.brokerServiceClass")
    .getOrElse("dev.boundarylab.broker.BoundaryBrokerService")
val bindPermission = providers.gradleProperty("boundarylab.brokerBindPermission")
    .getOrElse("dev.boundarylab.permission.BIND_BROKER")
fun checkedName(value: String): String {
    require(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+").matches(value)) {
        "Broker package, service and permission must be qualified Android names"
    }
    return value
}

android {
    namespace = "dev.boundarylab.consumer"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.boundarylab.standalone"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["boundarylabBrokerPackage"] = checkedName(brokerPackage)
        manifestPlaceholders["boundarylabBindPermission"] = checkedName(bindPermission)
        buildConfigField("String", "BROKER_PACKAGE", "\"${checkedName(brokerPackage)}\"")
        buildConfigField("String", "BROKER_SERVICE", "\"${checkedName(brokerService)}\"")
        buildConfigField("String", "BIND_PERMISSION", "\"${checkedName(bindPermission)}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Core APIs used by this app arrive transitively through this one declaration.
    implementation("dev.boundarylab:boundarylab-android:${providers.gradleProperty("boundarylabVersion").getOrElse("0.1.0-SNAPSHOT")}")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
