plugins {
    id("com.jiugjk.iq33.convention.feature")
}

android {
    namespace = "com.jiugjk.iq33.feature.base"
}

dependencies {
    // The whole project's only reflection user: StateTimeTravelDebugger walks a state's properties
    // to log what changed between two of them, in debug builds only.
    implementation(libs.kotlin.reflect)
}
