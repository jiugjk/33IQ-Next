plugins {
    id("com.jiugjk.iq33.convention.feature")
}

android {
    namespace = "com.jiugjk.iq33.feature.settings"
}

dependencies {
    implementation(libs.aboutlibraries.compose)
    implementation(projects.library.network)
}
