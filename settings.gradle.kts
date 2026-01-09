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

rootProject.name = "iCalDAV"

include(":icalendar-core")
include(":webdav-core")
include(":caldav-core")
include(":caldav-sync")
include(":ics-subscription")
// TODO: Implement caldav-android module when Android-specific utilities are needed
// TODO: Add sample module with usage examples
