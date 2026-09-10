package com.jiugjk.iq33.feature.base.presentation.viewmodel

import com.jiugjk.iq33.feature.base.util.TimberLogTags
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Logs actions and view state transitions to facilitate debugging.
 *
 * Two things this deliberately does *not* do:
 *
 * - cache one property list for the whole view model. States are a sealed hierarchy, and a screen's
 *   first state is usually a property-less `Loading`/`Idle` object; a list taken from it stays empty
 *   and every later transition would log nothing but the action name. Properties are read per
 *   transition, from both states, and the reflection result is cached per concrete state class.
 * - keep the whole timeline. Entries hold hard references to the states they describe (a full page
 *   of questions, a revealed answer), and an unbounded list keeps every one of them alive for as
 *   long as the view model lives. Only the most recent [MAX_TIMELINE_ENTRIES] are kept.
 */
class StateTimeTravelDebugger(
    private val viewClassName: String,
) {
    private val stateTimeline = ArrayDeque<StateTransition>()
    private var lastViewAction: BaseAction<*>? = null

    fun addAction(viewAction: BaseAction<*>) {
        lastViewAction = viewAction
    }

    fun addStateTransition(
        oldState: BaseState,
        newState: BaseState,
    ) {
        val lastViewAction = checkNotNull(lastViewAction) { "lastViewAction is null. Please log action before logging state transition" }

        if (stateTimeline.size >= MAX_TIMELINE_ENTRIES) stateTimeline.removeFirst()

        // Store redacted snapshots only: a LoginUiState with a real password must not live in this
        // list, even if the printed line later masks the field.
        stateTimeline.addLast(StateTransition(oldState.forLog(), lastViewAction, newState.forLog()))
        this.lastViewAction = null
    }

    /**
     * Dumps the retained timeline.
     *
     * Nothing calls this: it is the manual entry point for the "time travel" this class exists for -
     * call it from a breakpoint or an evaluate-expression window on a debug build to see how a
     * screen reached its current state. [logLast] is what runs automatically on every transition.
     */
    fun logAll() {
        Timber.tag(TimberLogTags.ACTION).d(getMessage(stateTimeline))
    }

    fun logLast() {
        val last = stateTimeline.lastOrNull() ?: return

        Timber.tag(TimberLogTags.ACTION).d(getMessage(listOf(last)))
    }

    private fun getMessage(stateTimeline: List<StateTransition>): String {
        if (stateTimeline.isEmpty()) return "$viewClassName has no state transitions\n"

        return stateTimeline.joinToString(separator = "\n", postfix = "\n") { transition ->
            buildString {
                append("Action: $viewClassName.${transition.action.javaClass.simpleName}")

                // The union of both states' properties: a transition between two different state
                // subclasses has no single property list, and either side alone would hide fields.
                val propertyNames = (transition.oldState.propertyNames() + transition.newState.propertyNames()).distinct()

                if (propertyNames.isNotEmpty()) {
                    append('\n')
                    append(
                        propertyNames.joinToString(separator = "") { property ->
                            getLogLine(transition.oldState, transition.newState, property)
                        },
                    )
                }
            }
        }
    }

    private fun getLogLine(
        oldState: BaseState,
        newState: BaseState,
        propertyName: String,
    ): String {
        val oldValue = getPropertyValue(oldState, propertyName)
        val newValue = getPropertyValue(newState, propertyName)
        val indent = "\t"

        return if (oldValue != newValue) {
            "$indent*$propertyName: $oldValue -> $newValue\n"
        } else {
            "$indent$propertyName: $newValue\n"
        }
    }

    private fun getPropertyValue(
        baseState: BaseState,
        propertyName: String,
    ): String {
        if (propertyName.lowercase() in SENSITIVE_PROPERTY_NAMES) return REDACTED

        val property = propertiesOf(baseState).firstOrNull { it.name == propertyName } ?: return ""
        val value = runCatching { property.getter.call(baseState).toString() }.getOrDefault("")

        return value.ifBlank { "\"\"" }
    }

    private fun BaseState.forLog(): BaseState = (this as? RedactableState)?.redactedForLog() ?: this

    private fun BaseState.propertyNames() = propertiesOf(this).map { it.name }

    /** Reflection is expensive, so it is cached - but per concrete state class, not per view model. */
    private fun propertiesOf(state: BaseState): List<KProperty1<out Any, *>> =
        propertiesByClass.getOrPut(state::class.java) { state::class.memberProperties.toList() }

    private data class StateTransition(
        val oldState: BaseState,
        val action: BaseAction<*>,
        val newState: BaseState,
    )

    private companion object {
        /** Enough recent history to read a flow of actions without pinning whole page loads forever. */
        const val MAX_TIMELINE_ENTRIES = 30

        const val REDACTED = "••••"

        val SENSITIVE_PROPERTY_NAMES = setOf("password", "token", "cookie", "cookies", "authorization")

        // Shared by every view model's debugger instance, so it must tolerate concurrent access.
        val propertiesByClass = ConcurrentHashMap<Class<*>, List<KProperty1<out Any, *>>>()
    }
}
