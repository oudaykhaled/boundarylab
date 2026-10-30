plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("dev.boundarylab.desktop.HostKt") }
dependencies {
    implementation(project(":core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
val nativeOutput = layout.buildDirectory.file("native/" + System.mapLibraryName("boundarylab_files"))
val compileNative by tasks.registering(Exec::class) {
    group = "build"
    description = "Compile the bounded POSIX descriptor bridge on macOS/Linux."
    val javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }.get().metadata.installationPath.asFile
    val osInclude = if (System.getProperty("os.name").contains("Mac")) "darwin" else "linux"
    inputs.file("src/main/c/native_files.c")
    outputs.file(nativeOutput)
    doFirst { nativeOutput.get().asFile.parentFile.mkdirs() }
    commandLine("cc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2", "-fPIC", "-shared",
        "-I${javaHome}/include", "-I${javaHome}/include/$osInclude",
        "src/main/c/native_files.c", "-o", nativeOutput.get().asFile.absolutePath)
}
tasks.test {
    dependsOn(compileNative)
    useJUnitPlatform()
    systemProperty("boundarylab.native.library", nativeOutput.get().asFile.absolutePath)
    systemProperty("boundarylab.test.classpath", sourceSets.main.get().runtimeClasspath.asPath)
}
tasks.named<JavaExec>("run") {
    dependsOn(compileNative)
    systemProperty("boundarylab.native.library", nativeOutput.get().asFile.absolutePath)
}
tasks.register("integrationTest") {
    group = "verification"
    dependsOn(tasks.test)
}
