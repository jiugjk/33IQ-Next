plugins {
    id("com.jiugjk.iq33.convention.feature")
    id("com.jiugjk.iq33.convention.aboutlibraries")
}

android {
    namespace = "com.jiugjk.iq33.feature.settings"
}

dependencies {
    implementation(libs.aboutlibraries.compose)
    implementation(projects.library.network)
}
