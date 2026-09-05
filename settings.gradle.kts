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

rootProject.name = "MochiStitch"
include(":app")
include(":core-common")
include(":core-imaging")
include(":core-mochismart")
include(":core-archive")
include(":core-settings")
include(":core-ui")
