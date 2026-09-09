plugins {
    id("com.jiugjk.iq33.convention.feature")
}

android {
    namespace = "com.jiugjk.iq33.feature.feed"
}

dependencies {
    implementation(projects.library.network)
    implementation(projects.feature.favourite)
    implementation(libs.serialization.json)
}
