package com.jiugjk.iq33.feature.base.presentation.viewmodel

interface BaseAction<State> {
    fun reduce(state: State): State
}
