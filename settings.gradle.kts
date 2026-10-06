pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "SysClean"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":fixture")

include(":core:model")
include(":core:domain")
include(":core:common")
include(":core:designsystem")
include(":core:ui")
include(":core:database")
include(":core:data")
include(":core:cleaner")
include(":core:performance")
include(":core:privilege")
include(":core:scanner")

include(":feature:dashboard")
include(":feature:deviceinfo")
include(":feature:apps")
include(":feature:settings")
include(":feature:cleaner")
include(":feature:trash")
include(":feature:widget")
include(":feature:performance")
