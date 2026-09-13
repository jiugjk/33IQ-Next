package com.jiugjk.iq33.feature.feed

import android.content.SharedPreferences
import com.jiugjk.iq33.feature.feed.data.datasource.database.AnswerRecordDao
import com.jiugjk.iq33.feature.feed.data.repository.FakeAnswerRecordDao
import com.jiugjk.iq33.feature.feed.data.repository.FakeSharedPreferences
import com.jiugjk.iq33.feature.feed.domain.repository.AnswerRecordRepository
import com.jiugjk.iq33.feature.feed.presentation.screen.feedlist.FeedListViewModel
import com.jiugjk.iq33.feature.feed.presentation.screen.history.HistoryViewModel
import com.jiugjk.iq33.library.network.IqHtmlClient
import com.jiugjk.iq33.library.network.IqSession
import com.jiugjk.iq33.library.network.SessionManager
import com.jiugjk.iq33.library.network.SessionStatus
import com.jiugjk.iq33.library.testutils.CoroutinesTestDispatcherExtension
import com.jiugjk.iq33.library.testutils.InstantTaskExecutorExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Resolves the real production modules.
 *
 * The point is that these objects are built *by Koin*, from `featureFeedModules` as shipped - not
 * constructed by hand in a test. Only the leaves that need a device (SharedPreferences, the Room
 * DAO, the HTTP client, the session) are substituted; every repository, use case and view model in
 * between is the real definition.
 *
 * This is what catches a constructor-DSL registration (`singleOf`) whose constructor has a parameter
 * the graph cannot provide: Kotlin's default value does not rescue it, the resolution simply fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantTaskExecutorExtension::class, CoroutinesTestDispatcherExtension::class)
class FeedGraphResolutionTest {
    private var application: KoinApplication? = null

    /** Everything a device would provide; the rest of the graph is the shipped definitions. */
    private val deviceLeaves =
        module {
            single<SharedPreferences> { FakeSharedPreferences() }
            single<AnswerRecordDao> { FakeAnswerRecordDao() }
            single {
                mockk<SessionManager> {
                    every { sessionFlow } returns MutableStateFlow(IqSession(SessionStatus.AUTHENTICATED, "10", "uid:1"))
                    every { sessionEpoch } returns 0
                }
            }
            single { mockk<IqHtmlClient>(relaxed = true) }
            single { OkHttpClient() }
        }

    @AfterEach
    fun stopKoin() {
        application?.close()
        application = null
    }

    @Test
    fun `the answer record repository resolves from the production module`() {
        val koin = start()

        koin.get<AnswerRecordRepository>() shouldBeInstanceOf AnswerRecordRepository::class
    }

    @Test
    fun `the screens that depend on it resolve too`() {
        val koin = start()

        koin.get<FeedListViewModel>() shouldBeInstanceOf FeedListViewModel::class
        koin.get<HistoryViewModel>() shouldBeInstanceOf HistoryViewModel::class
    }

    @Test
    fun `the repository is a singleton, so every screen observes the same history`() {
        val koin = start()

        val first = koin.get<AnswerRecordRepository>()
        val second = koin.get<AnswerRecordRepository>()

        (first === second) shouldBeEqualTo true
    }

    private fun start() =
        koinApplication {
            modules(featureFeedModules + deviceLeaves)
        }.also { application = it }.koin
}
