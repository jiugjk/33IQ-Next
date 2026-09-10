package com.jiugjk.iq33.buildlogic

import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AboutLibrariesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.mikepenz.aboutlibraries.plugin.android")
            }

            extensions.configure<AboutLibrariesExtension> {
                library {
                    // Avoids duplicate entries in the generated about libraries screen
                    duplicationMode.set(DuplicateMode.MERGE)
                }

                collect {
                    // Runtime/compile classpaths only. `all = true` skips those filters and pulls in
                    // BOMs, debug tooling and compile-only artifacts that are not what the APK ships,
                    // which is what made the in-app licence list disagree with the real dependency graph.
                    all.set(false)
                    includeTestVariants.set(false)
                    includePlatform.set(false)
                }
            }
        }
    }
}
