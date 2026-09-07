import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.jiugjk.iq33.buildlogic"

/*
Configure the build-logic plugins to target JDK from version catalog
This matches the JDK used to build the project, and is not related to what is running on device.
*/
val javaVersion =
    libs
        .versions
        .java
        .get()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion)
    }

    jvmToolchain(javaVersion.toInt())
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.test.logger.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.junit5.gradlePlugin)
    implementation(libs.easy.launcher.gradlePlugin)
    implementation(libs.about.libraries.gradlePlugin)

    /*
    Expose generated type-safe version catalogs accessors accessible from precompiled script plugins
    e.g. add("implementation", libs.koin)
    https://github.com/gradle/gradle/issues/15383
     */
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("applicationConvention") {
            id = "com.jiugjk.iq33.convention.application"
            implementationClass = "com.jiugjk.iq33.buildlogic.ApplicationConventionPlugin"
        }

        register("featureConvention") {
            id = "com.jiugjk.iq33.convention.feature"
            implementationClass = "com.jiugjk.iq33.buildlogic.FeatureConventionPlugin"
        }

        register("libraryConvention") {
            id = "com.jiugjk.iq33.convention.library"
            implementationClass = "com.jiugjk.iq33.buildlogic.LibraryConventionPlugin"
        }

        register("kotlinConvention") {
            id = "com.jiugjk.iq33.convention.kotlin"
            implementationClass = "com.jiugjk.iq33.buildlogic.KotlinConventionPlugin"
        }

        register("testConvention") {
            id = "com.jiugjk.iq33.convention.test"
            implementationClass = "com.jiugjk.iq33.buildlogic.TestConventionPlugin"
        }

        register("testLibraryConvention") {
            id = "com.jiugjk.iq33.convention.test.library"
            implementationClass = "com.jiugjk.iq33.buildlogic.TestConventionLibraryPlugin"
        }

        register("spotlessConvention") {
            id = "com.jiugjk.iq33.convention.spotless"
            implementationClass = "com.jiugjk.iq33.buildlogic.SpotlessConventionPlugin"
        }

        register("detektConvention") {
            id = "com.jiugjk.iq33.convention.detekt"
            implementationClass = "com.jiugjk.iq33.buildlogic.DetektConventionPlugin"
        }

        register("easyLauncherConvention") {
            id = "com.jiugjk.iq33.convention.easylauncher"
            implementationClass = "com.jiugjk.iq33.buildlogic.EasyLauncherConventionPlugin"
        }

        register("aboutLibrariesConvention") {
            id = "com.jiugjk.iq33.convention.aboutlibraries"
            implementationClass = "com.jiugjk.iq33.buildlogic.AboutLibrariesConventionPlugin"
        }
    }
}
