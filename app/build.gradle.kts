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
            isMinifyEnabled = false
            proguardFiles("proguard-android.txt", "proguard-rules.pro")
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
