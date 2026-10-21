pluginManagement {
    val foojayResolverVersion: String by settings
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") }

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

rootProject.name = "shop-orders"
