val boundarylabRepository = providers.gradleProperty("boundarylabRepository").orNull
    ?: error("Set -PboundarylabRepository=/absolute/path/to/maven or ORG_GRADLE_PROJECT_boundarylabRepository to the published BoundaryLab Maven folder.")

allprojects {
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "BoundaryLabLocal"
                    url = rootProject.uri(boundarylabRepository)
                    require(url.scheme == "file") { "The example expects a local BoundaryLab Maven folder." }
                }
            }
            filter { includeGroup("dev.boundarylab") }
        }
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
