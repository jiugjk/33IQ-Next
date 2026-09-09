package com.jiugjk.iq33.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.jiugjk.iq33.buildlogic.ext.libs
import com.jiugjk.iq33.buildlogic.ext.versions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.diffplug.spotless")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt", "**/*.kts")

                    // Some rules are disabled in .editorconfig to avoid conflicts with detekt.
                    //
                    // Only genuinely *custom* rule sets belong here. ktlint's own standard rules are
                    // built into the engine; this list used to name them as well, which registered a
                    // second, separately-configured copy of every standard rule - one that
                    // .editorconfig's ktlint_standard_* switches never reached, so a rule turned off
                    // there stayed on. The Twitter Compose ruleset is gone for a different reason:
                    // it was donated to, and became, io.nlopez.compose.rules, which is right below.
                    val customRuleSets = listOf(libs.nlopez.compose.rules).map { it.get().toString() }

                    ktlint(versions.ktlint.get())
                        .customRuleSets(customRuleSets)

                    endWithNewline()
                }

                // Don't add spotless as dependency for the Gradle's check task to facilitate separated codebase checks
                isEnforceCheck = false
            }
        }
    }
}
