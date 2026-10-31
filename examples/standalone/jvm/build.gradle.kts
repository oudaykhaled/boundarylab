plugins {
    kotlin("jvm") version "2.2.21"
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("MainKt") }
dependencies {
    implementation("dev.boundarylab:boundarylab-core:${providers.gradleProperty("boundarylabVersion").getOrElse("0.1.0-SNAPSHOT")}")
}
