package com.jiugjk.iq33.buildlogic

import com.android.build.api.dsl.LibraryExtension
import com.jiugjk.iq33.buildlogic.config.JavaBuildConfig
import com.jiugjk.iq33.buildlogic.ext.excludeLicenseAndMetaFiles
import com.jiugjk.iq33.buildlogic.ext.versions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class LibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply<KotlinConventionPlugin>()
                apply<TestConventionPlugin>()
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
                    viewBinding = false
                    buildConfig = true
                    // Compose is NOT enabled here. These are plain library modules (networking,
                    // test helpers) with no @Composable code; enabling it forced every one of them
                    // to depend on the whole Compose runtime just to satisfy the compiler plugin.
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
        }
    }
}
