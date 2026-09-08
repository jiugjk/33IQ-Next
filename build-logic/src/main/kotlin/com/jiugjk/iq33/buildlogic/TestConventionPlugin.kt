package com.jiugjk.iq33.buildlogic

import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.theme.ThemeType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

class TestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.adarshr.test-logger")

            tasks.withType<Test> {
                useJUnitPlatform()

                // Tests run sequentially, and say so.
                //
                // The previous configuration claimed to enable parallel execution but spelled the
                // mode key with a trailing space ("...mode.default "), which JUnit does not read -
                // so execution has always been sequential. Simply fixing the typo would not be safe
                // either: the shared test extensions install process-wide state
                // (CoroutinesTestDispatcherExtension replaces Dispatchers.Main,
                // InstantTaskExecutorExtension replaces the ArchTaskExecutor delegate) and reset it
                // in afterEach, so concurrent tests would tear down each other's globals. Turning
                // parallelism on needs those extensions isolated first.
                systemProperties =
                    mapOf(
                        "junit.jupiter.execution.parallel.enabled" to "false",
                        "junit.jupiter.execution.parallel.mode.default" to "same_thread",
                    )
            }

            extensions.configure<TestLoggerExtension> {
                theme = ThemeType.MOCHA
            }
        }
    }
}
