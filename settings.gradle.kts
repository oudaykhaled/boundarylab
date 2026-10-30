pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BoundaryLab"
include(":core", ":sdk:kotlin", ":android:broker", ":examples:android-native", ":desktop:host", ":desktop:cli")
include(":android:denied-client")
