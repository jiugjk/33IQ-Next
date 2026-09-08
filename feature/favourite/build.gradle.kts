plugins {
    id("com.jiugjk.iq33.convention.feature")
    // The only module with a database, so the only one that needs Room and its KSP processor.
    id("com.jiugjk.iq33.convention.room")
}

android {
    namespace = "com.jiugjk.iq33.feature.favourite"
}
