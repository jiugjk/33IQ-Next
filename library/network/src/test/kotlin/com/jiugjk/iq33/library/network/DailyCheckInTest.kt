package com.jiugjk.iq33.library.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class DailyCheckInTest {
    private val htmlClient = mockk<IqHtmlClient>()
    private val preferences = FakeSharedPreferences()
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 9, 10)
    private val clock = Clock.fixed(day.atTime(12, 0).atZone(shanghai).toInstant(), shanghai)
    private val sut = DailyCheckIn(htmlClient, preferences, clock)

    @Test
    fun `a successful check-in is not repeated the same day`() =
        runTest {
            stubSuccess()

            sut.runIfDue() shouldBeInstanceOf DailyCheckInResult.Ran::class
            sut.runIfDue() shouldBeEqualTo DailyCheckInResult.SkippedAlreadyDone

            coVerify(exactly = 1) { htmlClient.postFormForText(IqConstants.SIGN_IN_URL, emptyMap()) }
            coVerify(exactly = 1) { htmlClient.postFormForText(IqConstants.DAILY_TASK_URL, lotteryParams()) }
        }

    @Test
    fun `a failed sign-in is retried on the next launch`() =
        runTest {
            coEvery { htmlClient.postFormForText(IqConstants.DAILY_TASK_URL, any()) } returns """{"status":"success"}"""
            coEvery { htmlClient.postFormForText(IqConstants.SIGN_IN_URL, any()) } throws IOException("offline")

            val first = sut.runIfDue() as DailyCheckInResult.Ran

            first.signIn shouldBeInstanceOf CheckInStepResult.Failed::class

            stubSuccess()

            sut.runIfDue() shouldBeInstanceOf DailyCheckInResult.Ran::class

            coVerify(exactly = 2) { htmlClient.postFormForText(IqConstants.SIGN_IN_URL, any()) }
        }

    @Test
    fun `a login wall is reported as not logged in and is not marked done`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } throws IqLoginRequiredException()

            val result = sut.runIfDue() as DailyCheckInResult.Ran

            result.signIn shouldBeEqualTo CheckInStepResult.NotLoggedIn
            sut.runIfDue() shouldBeInstanceOf DailyCheckInResult.Ran::class
        }

    @Test
    fun `a guest status is not marked done`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"guest"}"""

            val result = sut.runIfDue() as DailyCheckInResult.Ran

            result.signIn shouldBeEqualTo CheckInStepResult.NotLoggedIn
            sut.runIfDue() shouldBeInstanceOf DailyCheckInResult.Ran::class
        }

    @Test
    fun `clearing the local mark lets another account check in the same day`() =
        runTest {
            stubSuccess()
            sut.runIfDue()

            sut.clearLocalMark()
            sut.runIfDue() shouldBeInstanceOf DailyCheckInResult.Ran::class

            coVerify(exactly = 2) { htmlClient.postFormForText(IqConstants.SIGN_IN_URL, emptyMap()) }
        }

    @Test
    fun `the next calendar day in Shanghai is a new check-in`() =
        runTest {
            stubSuccess()
            sut.runIfDue()

            val nextDay =
                Clock.fixed(
                    day
                        .plusDays(1)
                        .atTime(12, 0)
                        .atZone(shanghai)
                        .toInstant(),
                    shanghai,
                )
            val tomorrow = DailyCheckIn(htmlClient, preferences, nextDay)

            tomorrow.runIfDue() shouldBeInstanceOf DailyCheckInResult.Ran::class

            coVerify(exactly = 2) { htmlClient.postFormForText(IqConstants.SIGN_IN_URL, emptyMap()) }
        }

    @Test
    fun `an error envelope is a failure, not a successful check-in`() =
        runTest {
            coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"error"}"""

            val result = sut.runIfDue() as DailyCheckInResult.Ran

            result.signIn shouldBeEqualTo CheckInStepResult.Failed("error")
            sut.runIfDue() shouldBeInstanceOf DailyCheckInResult.Ran::class
        }

    private fun stubSuccess() {
        coEvery { htmlClient.postFormForText(any(), any()) } returns """{"status":"success"}"""
    }

    private fun lotteryParams() = mapOf("tasktype" to "lottery", "reason" to "")
}
