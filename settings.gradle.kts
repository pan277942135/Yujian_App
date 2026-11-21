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

rootProject.name = "Yujian"
include(
    ":recognition-domain",
    ":recognition-runtime",
    ":waterlight-renderer",
    ":application-core",
    ":recognition-ui",
    ":recognition-result-components",
    ":recognition-result-ui",
    ":app",
)
