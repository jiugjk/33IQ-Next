plugins {
    id("com.jiugjk.iq33.convention.library")
}

android {
    namespace = "com.jiugjk.iq33.library.network"
}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.core.ktx)
    implementation(libs.timber)
    implementation(libs.coroutines)

    implementation(libs.okhttp)
    implementation(libs.okhttp.interceptor)
    implementation(libs.jsoup)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin)
}
