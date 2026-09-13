package com.jiugjk.iq33.feature.feed.presentation.screen.questiondetail

import com.jiugjk.iq33.feature.feed.data.datasource.remote.QuestionJsonParser
import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class WordBankActionTest {
    @Test
    fun `a single character answer can be selected replaced and deselected`() {
        val initial = content()
        initial.canSubmitAnswer("") shouldBeEqualTo false
        val first = toggle(initial, 0)
        first.wordBankAnswer shouldBeEqualTo "甲"
        first.canSubmitAnswer("甲") shouldBeEqualTo true
        first.canSubmitAnswer("A") shouldBeEqualTo false
        first.canSubmitAnswer("0") shouldBeEqualTo false
        first.canSubmitAnswer("乙") shouldBeEqualTo false
        val replaced = toggle(first, 1)
        replaced.wordBankAnswer shouldBeEqualTo "乙"
        toggle(replaced, 1).wordBankAnswer shouldBeEqualTo ""
    }

    @Test
    fun `duplicate tiles are separate choices and form a repeated character answer`() {
        val state = content(candidates = listOf("保", "保", "甲"), length = 2)
        val one = toggle(state, 0)
        one.canSubmitAnswer("保") shouldBeEqualTo false
        val two = toggle(one, 1)
        two.wordBankAnswer shouldBeEqualTo "保保"
        two.canSubmitAnswer("保保") shouldBeEqualTo true
        two.selectedCandidateIndices shouldBeEqualTo listOf(0, 1)
        toggle(two, 2) shouldBeEqualTo two
        toggle(two, 0).selectedCandidateIndices shouldBeEqualTo listOf(1)
    }

    @Test
    fun `the selection follows tap order and rejects unknown tile indices`() {
        val selected = toggle(toggle(content(length = 2), 1), 0)
        selected.wordBankAnswer shouldBeEqualTo "乙甲"
        toggle(selected, -1) shouldBeEqualTo selected
        toggle(selected, 99) shouldBeEqualTo selected
    }

    @Test
    fun `clear and raw draft edits cannot bypass word bank validation`() {
        val selected = toggle(content(), 0)
        QuestionDetailAction.DraftAnswerChanged("乙").reduce(selected) shouldBeEqualTo selected
        val cleared = QuestionDetailAction.CandidatesCleared.reduce(selected) as QuestionDetailUiState.Content
        cleared.wordBankAnswer shouldBeEqualTo ""
        cleared.canSubmitAnswer("甲") shouldBeEqualTo false
        QuestionDetailAction.SubmissionStarted(1, "甲").reduce(cleared) shouldBeEqualTo cleared
    }

    @Test
    fun `word bank text is submitted verbatim and edits freeze until the result arrives`() {
        val selected = toggle(content(), 1)
        val submitting =
            QuestionDetailAction.SubmissionStarted(1, selected.wordBankAnswer).reduce(selected) as QuestionDetailUiState.Content
        (submitting.submission as SubmissionState.Submitting).submittedAnswer shouldBeEqualTo "乙"
        toggle(submitting, 0) shouldBeEqualTo submitting
        QuestionDetailAction.CandidatesCleared.reduce(submitting) shouldBeEqualTo submitting
        val failed = QuestionDetailAction.SubmissionFailed(1).reduce(submitting) as QuestionDetailUiState.Content
        failed.canSubmitAnswer("乙") shouldBeEqualTo true
    }

    @Test
    fun `missing candidates or length prevent selection and submission`() {
        listOf(content(candidates = emptyList()), content(length = null), content(length = 0)).forEach { state ->
            state.isWordBankReady shouldBeEqualTo false
            toggle(state, 0) shouldBeEqualTo state
            state.canSubmitAnswer("甲") shouldBeEqualTo false
        }
    }

    @Test
    fun `supplementary Chinese characters count as one character rather than two UTF16 units`() {
        val selected = toggle(content(candidates = listOf("𠀀"), length = 1), 0)
        selected.canSubmitAnswer("𠀀") shouldBeEqualTo true
    }

    @Test
    fun `forged repeated indices cannot reuse the same tile twice`() {
        val state = content(length = 2).copy(selectedCandidateIndices = listOf(0, 0))
        state.canSubmitAnswer("甲甲") shouldBeEqualTo false
    }

    @Test
    fun `copying a word bank includes its candidates and preserves duplicates`() {
        val state = content(candidates = listOf("保", "保", "甲"))
        questionAsPlainText(state.detail) shouldBeEqualTo "题\n\n保 · 保 · 甲"
    }

    private fun toggle(
        state: QuestionDetailUiState.Content,
        index: Int,
    ) = QuestionDetailAction.CandidateToggled(index).reduce(state) as QuestionDetailUiState.Content

    private fun content(
        candidates: List<String> = listOf("甲", "乙"),
        length: Int? = 1,
    ): QuestionDetailUiState.Content {
        val detail =
            QuestionJsonParser()
                .parseQuestionDetail("""[{"qc_context":"题"}]""", 1)!!
                .copy(questionType = QuestionType.WORD_BANK, answerCandidates = candidates, answerLength = length)
        return QuestionDetailUiState.Content(detail, isBookmarked = false)
    }
}
