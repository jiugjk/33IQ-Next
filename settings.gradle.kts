rootProject.name = "iq33-next"

include(
    ":app",
    ":feature:feed",
    ":feature:auth",
    ":feature:settings",
    ":feature:favourite",
    ":feature:base",
    ":library:network",
    ":library:test-utils",
    ":konsist-test",
)

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        // Added for testing local Konsist artifacts
        mavenLocal()
        mavenCentral()
    }
}

// Generate type safe accessors when referring to other projects eg.
// Before: implementation(project(":feature:feed"))
// After: implementation(projects.feature.feed)
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
