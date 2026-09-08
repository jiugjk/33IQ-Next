package com.jiugjk.iq33.feature.settings.presentation.screen.aboutlibraries

import com.jiugjk.iq33.library.testutils.CoroutinesTestDispatcherExtension
import com.jiugjk.iq33.library.testutils.InstantTaskExecutorExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantTaskExecutorExtension::class, CoroutinesTestDispatcherExtension::class)
class AboutLibrariesViewModelTest {
    private val sut = AboutLibrariesViewModel()

    @Test
    fun `initial state should be Content`() =
        runTest {
            // then
            sut.uiStateFlow.value shouldBeEqualTo AboutLibrariesUiState.Content
        }
}
