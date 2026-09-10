package com.jiugjk.iq33.feature.base.presentation.viewmodel

/**
 * A [BaseState] that contains credentials or other secrets the debug state logger must never keep.
 *
 * Returning a snapshot without those fields (rather than only masking the printed line) is what
 * stops a time-travel debugger from pinning the original password in memory for the view model's
 * whole lifetime.
 */
interface RedactableState : BaseState {
    fun redactedForLog(): BaseState
}
