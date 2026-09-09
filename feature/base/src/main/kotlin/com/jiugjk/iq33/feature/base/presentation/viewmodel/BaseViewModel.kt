package com.jiugjk.iq33.feature.base.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.jiugjk.iq33.feature.base.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Base for the screen view models: an action is reduced against the current state and the result is
 * published.
 *
 * [MutableStateFlow.update] is the single source of truth. The read-reduce-write runs atomically, so
 * two coroutines sending actions at once cannot each reduce a stale snapshot and drop the other's
 * transition.
 */
abstract class BaseViewModel<State : BaseState, Action : BaseAction<State>>(
    initialState: State,
) : ViewModel() {
    private val _uiStateFlow = MutableStateFlow(initialState)
    val uiStateFlow = _uiStateFlow.asStateFlow()

    private val stateTimeTravelDebugger: StateTimeTravelDebugger? =
        if (BuildConfig.DEBUG) StateTimeTravelDebugger(this::class.java.simpleName) else null

    /**
     * Reduces [action] against the current state. Reducers are pure; logging below runs only for a
     * transition that was actually published.
     */
    protected fun sendAction(action: Action) {
        var transition: Pair<State, State>? = null

        _uiStateFlow.update { oldState ->
            val newState = action.reduce(oldState)

            if (oldState != newState) {
                transition = oldState to newState
            }

            newState
        }

        val logged = transition ?: return

        stateTimeTravelDebugger?.apply {
            addAction(action)
            addStateTransition(logged.first, logged.second)
            logLast()
        }
    }
}
