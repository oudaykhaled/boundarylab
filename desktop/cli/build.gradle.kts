plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("dev.boundarylab.desktop.cli.MainKt") }
dependencies {
    implementation(project(":core"))
    implementation(project(":desktop:host"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
tasks.test {
    useJUnitPlatform()
    inputs.files(rootProject.file("conformance/shared/manifest-v1.json"), rootProject.file("conformance/shared/digest-v1.json"),
        rootProject.file("examples/fieldpack/fieldpack.py"))
    systemProperty("boundarylab.test.classpath", sourceSets.main.get().runtimeClasspath.asPath)
    systemProperty("boundarylab.project.root", rootProject.projectDir.absolutePath)
    dependsOn(":desktop:host:compileNative")
    systemProperty("boundarylab.native.library", providers.systemProperty("boundarylab.native.library").getOrElse(
        rootProject.file("desktop/host/build/native/" + System.mapLibraryName("boundarylab_files")).absolutePath))
}
