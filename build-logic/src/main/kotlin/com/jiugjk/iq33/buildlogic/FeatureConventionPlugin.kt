package com.jiugjk.iq33.buildlogic

import com.android.build.api.dsl.LibraryExtension
import com.jiugjk.iq33.buildlogic.config.JavaBuildConfig
import com.jiugjk.iq33.buildlogic.ext.debugImplementation
import com.jiugjk.iq33.buildlogic.ext.excludeLicenseAndMetaFiles
import com.jiugjk.iq33.buildlogic.ext.implementation
import com.jiugjk.iq33.buildlogic.ext.ksp
import com.jiugjk.iq33.buildlogic.ext.libs
import com.jiugjk.iq33.buildlogic.ext.testImplementation
import com.jiugjk.iq33.buildlogic.ext.testRuntimeOnly
import com.jiugjk.iq33.buildlogic.ext.versions
import com.mikepenz.aboutlibraries.plugin.AboutLibrariesPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

@Suppress("detekt.LongMethod")
class FeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply<KotlinConventionPlugin>()
                apply<TestConventionPlugin>()
                apply<AboutLibrariesPlugin>()
                apply("com.google.devtools.ksp")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.configure<LibraryExtension> {
                compileSdk =
                    versions
                        .compile
                        .sdk
                        .get()
                        .toInt()

                defaultConfig {
                    minSdk =
                        versions
                            .min
                            .sdk
                            .get()
                            .toInt()

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                }

                buildFeatures {
                    viewBinding = true
                    buildConfig = true
                    compose = true
                }

                compileOptions {
                    sourceCompatibility = JavaBuildConfig.JAVA_VERSION
                    targetCompatibility = JavaBuildConfig.JAVA_VERSION
                }

                testOptions {
                    unitTests.isReturnDefaultValues = true
                }

                packaging {
                    excludeLicenseAndMetaFiles()
                }
            }

            dependencies {
                // Add feature:base dependency only for non-base feature modules
                if (project.path != ":feature:base") {
                    implementation(project(":feature:base"))
                }

                implementation(libs.kotlin.reflect)
                implementation(libs.core.ktx)
                implementation(libs.timber)
                implementation(libs.coroutines)
                implementation(libs.material.material)
                implementation(libs.compose.material)
                implementation(libs.material.icons)

                // Compose dependencies
                implementation(platform(libs.compose.bom))
                implementation(libs.bundles.compose)
                debugImplementation(libs.compose.ui.tooling)
                debugImplementation(libs.compose.ui.test.manifest)

                // Koin
                implementation(platform(libs.koin.bom))
                implementation(libs.bundles.koin)

                implementation(libs.bundles.retrofit)
                implementation(libs.viewmodel.ktx)

                // Room
                implementation(libs.bundles.room)
                ksp(libs.room.compiler)

                // Test dependencies
                testImplementation(project(":library:test-utils"))
                testImplementation(libs.bundles.test)
                testRuntimeOnly(libs.junit.jupiter.engine)
            }
        }
    }
}
