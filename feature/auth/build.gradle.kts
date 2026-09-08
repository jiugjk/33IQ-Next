plugins {
    id("com.jiugjk.iq33.convention.feature")
}

android {
    namespace = "com.jiugjk.iq33.feature.auth"
}

dependencies {
    implementation(projects.library.network)
}
