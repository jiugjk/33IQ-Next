plugins {
    id("com.jiugjk.iq33.convention.library")
}

android {
    namespace = "com.jiugjk.iq33.library.testutils"
}

dependencies {
    // implementation configuration is used here (instead of testImplementation) because this module is added as
    // testImplementation dependency inside other modules. Using implementation allows to write tests for test utilities.
    implementation(libs.bundles.test)

    runtimeOnly(libs.junit.jupiter.engine)
}
