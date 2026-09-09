plugins {
    id("com.jiugjk.iq33.convention.application")
}

/*
 * Signing material comes from the environment - GitHub Actions secrets in CI, exported variables or
 * ~/.gradle/gradle.properties locally - and never from a file in this repository: a keystore or its
 * passwords committed here would be public the moment the repo is.
 *
 * When it is absent the build still works: debug keeps the throwaway key AGP generates and release
 * comes out unsigned (app-release-unsigned.apk). That is what lets a pull request from a fork, or a
 * fresh clone with no secrets, still build instead of failing on missing credentials.
 *
 * When it is present, both build types are signed with that one key, so each APK CI publishes can be
 * installed over the previous one rather than rejected for a mismatched signature.
 */
val signingConfigName = "upload"

val signingKeystore = providers.environmentVariable("SIGNING_KEYSTORE_FILE").orElse(providers.gradleProperty("signingKeystoreFile"))
val signingKeystorePassword =
    providers.environmentVariable("SIGNING_KEYSTORE_PASSWORD").orElse(providers.gradleProperty("signingKeystorePassword"))
val signingKeyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orElse(providers.gradleProperty("signingKeyAlias"))
val signingKeyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orElse(providers.gradleProperty("signingKeyPassword"))

val hasSigningMaterial =
    listOf(signingKeystore, signingKeystorePassword, signingKeyAlias, signingKeyPassword)
        .all { value -> value.orNull?.isNotBlank() == true }

android {
    namespace = "com.jiugjk.iq33.app"

    defaultConfig {
        applicationId = "com.jiugjk.iq33"

        versionCode = 1
        versionName = "0.0.1" // SemVer (Major.Minor.Patch)
    }

    signingConfigs {
        if (hasSigningMaterial) {
            create(signingConfigName) {
                // rootProject.file resolves an absolute path as-is, so CI can hand over a keystore
                // it decoded into the runner's temp directory.
                storeFile = rootProject.file(signingKeystore.get())
                storePassword = signingKeystorePassword.get()
                keyAlias = signingKeyAlias.get()
                keyPassword = signingKeyPassword.get()
            }
        }
    }

    val uploadSigningConfig = signingConfigs.findByName(signingConfigName)

    buildTypes {
        getByName("debug") {
            // Only when a key was supplied - otherwise this build type keeps AGP's generated debug
            // key, which is what a developer without the secrets should get.
            uploadSigningConfig?.let { signingConfig = it }
        }

        getByName("release") {
            signingConfig = uploadSigningConfig

            // Now that the dead Retrofit adapter chain and the preview-only composables are gone,
            // R8 has something to work with: it strips what is left unreachable and the resource
            // shrinker drops the resources that go with it. See proguard-rules.pro for the entry
            // points that must survive (they are reached reflectively, not from call sites).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    // "projects." Syntax utilizes Gradle TYPESAFE_PROJECT_ACCESSORS feature
    implementation(projects.feature.base)
    implementation(projects.feature.feed)
    implementation(projects.feature.auth)
    implementation(projects.feature.settings)
    implementation(projects.feature.favourite)
    implementation(projects.library.network)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
}
