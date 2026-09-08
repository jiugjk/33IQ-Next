package com.jiugjk.iq33.buildlogic

import com.jiugjk.iq33.buildlogic.ext.implementation
import com.jiugjk.iq33.buildlogic.ext.ksp
import com.jiugjk.iq33.buildlogic.ext.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Adds Room and its KSP processor.
 *
 * Applied per module rather than from the feature convention: only the module that actually declares
 * a database needs the annotation processor, and running KSP in every feature module costs a
 * processing round each without producing anything.
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            dependencies {
                implementation(libs.bundles.room)
                ksp(libs.room.compiler)
            }
        }
    }
}
