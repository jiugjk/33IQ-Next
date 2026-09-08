package com.jiugjk.iq33.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.jiugjk.iq33.buildlogic.config.JavaBuildConfig
import com.jiugjk.iq33.buildlogic.ext.debugImplementation
import com.jiugjk.iq33.buildlogic.ext.excludeLicenseAndMetaFiles
import com.jiugjk.iq33.buildlogic.ext.implementation
import com.jiugjk.iq33.buildlogic.ext.libs
import com.jiugjk.iq33.buildlogic.ext.versions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

@Suppress("detekt.LongMethod")
class ApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("com.google.devtools.ksp")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply<KotlinConventionPlugin>()
                apply<SpotlessConventionPlugin>()
                apply<EasyLauncherConventionPlugin>()
                apply<AboutLibrariesConventionPlugin>()
            }

            extensions.configure<ApplicationExtension> {
                compileSdk =
                    versions
                        .compile
                        .sdk
                        .get()
                        .toInt()

                defaultConfig {
                    applicationId = "com.jiugjk.iq33"

                    minSdk =
                        versions
                            .min
                            .sdk
                            .get()
                            .toInt()

                    targetSdk =
                        versions
                            .target
                            .sdk
                            .get()
                            .toInt()

                    versionCode = 1
                    versionName = "1.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    multiDexEnabled = true

                    vectorDrawables {
                        useSupportLibrary = true
                    }
                }

                buildFeatures {
                    viewBinding = false
                    buildConfig = true
                    compose = true
                }

                compileOptions {
                    sourceCompatibility = JavaBuildConfig.JAVA_VERSION
                    targetCompatibility = JavaBuildConfig.JAVA_VERSION
                }

                packaging {
                    excludeLicenseAndMetaFiles()
                }

                testOptions {
                    unitTests.isReturnDefaultValues = true
                }
            }

            dependencies {
                implementation(libs.core.ktx)
                implementation(libs.timber)
                implementation(libs.coroutines)
                implementation(libs.material.material)
                implementation(libs.compose.material)
                implementation(libs.material.icons)

                // Compose dependencies
                implementation(platform(libs.compose.bom))
                implementation(libs.tooling.preview)
                debugImplementation(libs.compose.ui.tooling)
                implementation(libs.navigation.compose)

                // Koin
                implementation(platform(libs.koin.bom))
                implementation(libs.bundles.koin)

                implementation(libs.bundles.network)
                implementation(libs.viewmodel.ktx)
                implementation(libs.core.splashscreen)
            }
        }
    }
}
