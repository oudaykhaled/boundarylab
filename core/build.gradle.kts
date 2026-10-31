plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jetbrains.dokka")
}

kotlin { jvmToolchain(17) }
java { withSourcesJar() }

publishing {
    publications.create<MavenPublication>("library") {
        artifactId = "boundarylab-core"
        from(components["java"])
        pom {
            name.set("BoundaryLab Core")
            description.set("Transport-independent bounded job engine and FieldPack codec for JVM 17.")
        }
    }
}
apply(from = rootProject.file("gradle/library-publication.gradle.kts"))

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test { useJUnitPlatform() }
