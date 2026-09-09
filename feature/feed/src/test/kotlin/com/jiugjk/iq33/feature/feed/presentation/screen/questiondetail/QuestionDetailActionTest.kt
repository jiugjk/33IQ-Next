package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.feed.domain.model.AnswerReveal
import com.jiugjk.iq33.feature.feed.domain.model.Choice
import com.jiugjk.iq33.feature.feed.domain.model.QuestionDetail
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import com.jiugjk.iq33.feature.feed.domain.model.SubmitAnswerResult
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class QuestionDetailActionTest {
    @Test
    fun `un-praising is not mistaken for a praise when others voted meanwhile`() {
        val state = content(upvoteCount = 100, isUpvoted = true).copy(isPraising = true)

        val reduced = QuestionDetailAction.Praised(questionId = 1, newCount = 102).reduce(state) as QuestionDetailUiState.Content

        reduced.detail.isUpvoted shouldBeEqualTo false
        reduced.detail.upvoteCount shouldBeEqualTo 102
    }

    @Test
    fun `a praise reply that was not requested is ignored`() {
        val state = content(upvoteCount = 100, isUpvoted = true)

        QuestionDetailAction.Praised(questionId = 1, newCount = 7).reduce(state) shouldBeEqualTo state
    }

    @Test
    fun `the choice cannot be changed while a submission is in flight`() {
        val submitting = QuestionDetailAction.SubmissionStarted(1, "A").reduce(content()) as QuestionDetailUiState.Content

        val reduced = QuestionDetailAction.ChoiceSelected("B").reduce(submitting) as QuestionDetailUiState.Content

        reduced.selectedChoiceId shouldBeEqualTo "A"
    }

    @Test
    fun `a submission result is recorded against the choice that was actually sent`() {
        val submitting = QuestionDetailAction.SubmissionStarted(1, "A").reduce(content())

        val reduced =
            QuestionDetailAction
                .SubmissionFinished(1, SubmitAnswerResult.Correct(scoreDelta = 2, myScore = 20))
                .reduce(submitting) as QuestionDetailUiState.Content

        (reduced.submission as SubmissionState.Done).choiceId shouldBeEqualTo "A"
    }

    @Test
    fun `a submission result that was never requested is ignored`() {
        val state = content()

        QuestionDetailAction.SubmissionFinished(1, SubmitAnswerResult.AlreadyAnswered).reduce(state) shouldBeEqualTo state
    }

    @Test
    fun `a submission result for another question is ignored`() {
        val submitting = QuestionDetailAction.SubmissionStarted(1, "A").reduce(content())

        QuestionDetailAction.SubmissionFinished(99, SubmitAnswerResult.Correct(1, 1)).reduce(submitting) shouldBeEqualTo submitting
    }

    @Test
    fun `revealing the answer is blocked while a submission is in flight`() {
        val submitting = QuestionDetailAction.SubmissionStarted(1, "A").reduce(content())

        val reduced = QuestionDetailAction.AnswerConfirmRequested.reduce(submitting) as QuestionDetailUiState.Content

        reduced.answerReveal shouldBeInstanceOf RevealState.Idle::class
    }

    @Test
    fun `submitting is blocked once the answer reveal flow has started`() {
        val confirming = QuestionDetailAction.AnswerConfirmRequested.reduce(content()) as QuestionDetailUiState.Content

        confirming.canSubmit shouldBeEqualTo false
        confirming.canSelectChoice shouldBeEqualTo false
    }

    @Test
    fun `a second reveal confirm is ignored while already revealing`() {
        val confirming = QuestionDetailAction.AnswerConfirmRequested.reduce(content()) as QuestionDetailUiState.Content
        val revealing = QuestionDetailAction.AnswerRevealStarted(1).reduce(confirming) as QuestionDetailUiState.Content

        revealing.answerReveal shouldBeInstanceOf RevealState.Revealing::class
        QuestionDetailAction.AnswerRevealStarted(1).reduce(revealing) shouldBeEqualTo revealing
    }

    @Test
    fun `a charged failure blocks another reveal of the same question`() {
        val confirming = QuestionDetailAction.AnswerConfirmRequested.reduce(content())
        val revealing = QuestionDetailAction.AnswerRevealStarted(1).reduce(confirming)
        val failed =
            QuestionDetailAction.AnswerFlowFailed(1, afterSideEffect = true).reduce(revealing) as QuestionDetailUiState.Content

        failed.canStartAnswerReveal shouldBeEqualTo false
        failed.answerReveal shouldBeEqualTo RevealState.Failed(afterSideEffect = true)
    }

    @Test
    fun `an uncharged failure can still be retried`() {
        val failed =
            QuestionDetailAction.AnswerFlowFailed(1, afterSideEffect = false).reduce(content()) as QuestionDetailUiState.Content

        failed.canStartAnswerReveal shouldBeEqualTo true
    }

    @Test
    fun `a reveal result for another question is ignored`() {
        val confirming = QuestionDetailAction.AnswerConfirmRequested.reduce(content())
        val revealing = QuestionDetailAction.AnswerRevealStarted(1).reduce(confirming)
        val reveal = AnswerReveal(answer = "A", explanation = "", cost = 60, alreadyPaid = false)

        QuestionDetailAction.AnswerRevealFinished(99, reveal).reduce(revealing) shouldBeEqualTo revealing
    }

    private fun content(
        upvoteCount: Int = 0,
        isUpvoted: Boolean = false,
    ) = QuestionDetailUiState.Content(
        detail =
            QuestionDetail(
                id = 1,
                title = "题",
                bodyText = "正文",
                imageUrls = emptyList(),
                tags = emptyList(),
                breadcrumb = emptyList(),
                author = null,
                publishedDate = null,
                upvoteCount = upvoteCount,
                isUpvoted = isUpvoted,
                commentCount = 0,
                collectCount = 0,
                rightRatio = null,
                questionType = QuestionType.CHOICE,
                choices = listOf(Choice("A", "选项一"), Choice("B", "选项二")),
                analysis = null,
                comments = emptyList(),
                sourceUrl = "https://www.33iq.com/question/1.html",
            ),
        isBookmarked = false,
    )
}
