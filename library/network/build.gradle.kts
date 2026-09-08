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
    // api: IqHtmlClient's public functions return org.jsoup.nodes.Document, so consumers
    // (feature/feed) need Jsoup types on their compile classpath too.
    api(libs.jsoup)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin)

    // This module has no Compose UI, but the library convention plugin applies the Compose
    // compiler plugin to every module unconditionally, which then requires the runtime on the
    // classpath even with zero @Composable usages.
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
}
