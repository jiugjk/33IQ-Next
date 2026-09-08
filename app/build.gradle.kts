plugins {
    id("com.jiugjk.iq33.convention.application")
}

android {
    namespace = "com.jiugjk.iq33.app"

    defaultConfig {
        applicationId = "com.jiugjk.iq33"

        versionCode = 1
        versionName = "0.0.1" // SemVer (Major.Minor.Patch)
    }

    buildTypes {
        getByName("release") {
            // Now that the dead Retrofit adapter chain and the preview-only composables are gone,
            // R8 has something to work with: it strips what is left unreachable and the resource
            // shrinker drops the resources that go with it. See proguard-rules.pro for the entry
            // points that must survive (they are reached reflectively, not from call sites).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    // "projects." Syntax utilizes Gradle TYPESAFE_PROJECT_ACCESSORS feature
    implementation(projects.feature.base)
    implementation(projects.feature.feed)
    implementation(projects.feature.auth)
    implementation(projects.feature.settings)
    implementation(projects.feature.favourite)
    implementation(projects.library.network)
}
