plugins {
    id("com.jiugjk.iq33.convention.library")
}

android {
    namespace = "com.jiugjk.iq33.library.network"
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.timber)
    implementation(libs.coroutines)

    implementation(libs.okhttp)
    implementation(libs.okhttp.interceptor)
    // SessionManager parses 33IQ's probe reply instead of pattern-matching its raw text.
    implementation(libs.serialization.json)
    // api: IqHtmlClient's public functions return org.jsoup.nodes.Document, so consumers
    // (feature/feed) need Jsoup types on their compile classpath too.
    api(libs.jsoup)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
