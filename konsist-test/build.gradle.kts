plugins {
    id("com.jiugjk.iq33.convention.test.library")
}

android {
    namespace = "com.jiugjk.iq33.konsist.test"
}

dependencies {
    implementation(projects.feature.base)

    testImplementation(projects.library.testUtils)
    testImplementation(libs.bundles.test)
    testImplementation(libs.konsist)
    testImplementation(libs.viewmodel.ktx)
}
