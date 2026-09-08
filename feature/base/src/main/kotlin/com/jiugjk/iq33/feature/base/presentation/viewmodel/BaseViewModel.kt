package com.jiugjk.iq33.feature.base.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.jiugjk.iq33.feature.base.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base for the screen view models: an action is reduced against the current state and the result is
 * published.
 *
 * [MutableStateFlow] is the single source of truth. It already suppresses an update that equals the
 * current value, so the observable-delegate copy this class used to keep alongside it only made it
 * ambiguous which of the two held "the" state and where the debug logging happened.
 */
abstract class BaseViewModel<State : BaseState, Action : BaseAction<State>>(
    initialState: State,
) : ViewModel() {
    private val _uiStateFlow = MutableStateFlow(initialState)
    val uiStateFlow = _uiStateFlow.asStateFlow()

    private val stateTimeTravelDebugger: StateTimeTravelDebugger? =
        if (BuildConfig.DEBUG) StateTimeTravelDebugger(this::class.java.simpleName) else null

    /**
     * Reduces [action] against the current state. Reducers are pure, so this is safe to call from
     * anywhere; the logging below runs only for a transition that was actually published.
     */
    protected fun sendAction(action: Action) {
        val oldState = _uiStateFlow.value
        val newState = action.reduce(oldState)

        if (oldState == newState) return

        _uiStateFlow.value = newState

        stateTimeTravelDebugger?.apply {
            addAction(action)
            addStateTransition(oldState, newState)
            logLast()
        }
    }
}
