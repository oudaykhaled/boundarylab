pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "BoundaryLabLocal"
                    url = uri(providers.gradleProperty("boundarylabRepository").get())
                    require(url.scheme == "file") { "boundarylabRepository must be a local folder" }
                }
            }
            filter { includeGroup("dev.boundarylab") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "BoundaryLabStandaloneAndroid"
