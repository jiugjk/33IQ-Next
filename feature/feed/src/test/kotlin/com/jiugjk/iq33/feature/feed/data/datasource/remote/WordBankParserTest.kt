package com.jiugjk.iq33.feature.feed.data.datasource.remote

import com.jiugjk.iq33.feature.feed.domain.model.QuestionType
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class WordBankParserTest {
    private val sut = QuestionJsonParser()

    @Test
    fun `589144 is a word bank despite ischoose being zero`() {
        val json = requireNotNull(javaClass.getResource("/question-589144-word-bank.json")).readText()
        val detail = requireNotNull(sut.parseQuestionDetail(json, 589_144))
        detail.questionType shouldBeEqualTo QuestionType.WORD_BANK
        detail.answerLength shouldBeEqualTo 1
        detail.choices shouldBeEqualTo emptyList()
        detail.answerCandidates shouldBeEqualTo
            listOf(
                "舍",
                "困",
                "保",
                "杗",
                "束",
                "呑",
                "和",
                "果",
                "木",
                "否",
                "保",
                "口",
                "杏",
                "享",
                "呁",
                "呂",
                "杲",
                "偿",
                "估",
                "昛",
                "牢",
                "宋",
                "亩",
                "杳",
            )
        detail.answerCandidates.count { it == "保" } shouldBeEqualTo 2
        // qc_wronganswer contains 佑, but the actual word bank does not. Never substitute decoys.
        ("佑" in detail.answerCandidates) shouldBeEqualTo false
    }

    @Test
    fun `normal multiple choice and open answers keep their existing interaction`() {
        val choice = sut.parseQuestionDetail("""[{"qc_context":"题", "ischoose":"1", "qc_choose_a":"甲"}]""", 1)!!
        choice.questionType shouldBeEqualTo QuestionType.CHOICE
        choice.choices.single().id shouldBeEqualTo "A"
        val open = sut.parseQuestionDetail("""[{"qc_context":"题", "ischoose":"0", "qc_wronganswer":"甲|乙"}]""", 1)!!
        open.questionType shouldBeEqualTo QuestionType.OPEN
        open.answerCandidates shouldBeEqualTo emptyList()
    }

    @Test
    fun `a declared word bank with missing data is not downgraded to unrestricted typing`() {
        val detail = sut.parseQuestionDetail("""[{"qc_context":"题", "is_select_answer":"1", "qc_wronganswer":"甲|乙"}]""", 1)!!
        detail.questionType shouldBeEqualTo QuestionType.WORD_BANK
        detail.answerCandidates shouldBeEqualTo emptyList()
        detail.answerLength shouldBeEqualTo null
    }

    @Test
    fun `malformed candidate arrays are rejected as a whole rather than silently losing tiles`() {
        val malformed =
            listOf(
                """["甲",null]""",
                """["甲",{}]""",
                """["甲",true]""",
                """["甲",1]""",
                """["甲",""]""",
            )
        malformed.forEach { candidates ->
            val json = """[{"qc_context":"题", "is_select_answer":"1", "select_answer":$candidates, "answerStrNum":"1"}]"""
            sut.parseQuestionDetail(json, 1)!!.answerCandidates shouldBeEqualTo emptyList()
        }
    }

    @Test
    fun `missing invalid and nonpositive answer lengths are unknown`() {
        listOf("null", "0", "-1", "\"abc\"", "{}", "99999999999999").forEach { length ->
            val json = """[{"qc_context":"题", "is_select_answer":"1", "select_answer":["甲"], "answerStrNum":$length}]"""
            sut.parseQuestionDetail(json, 1)!!.answerLength shouldBeEqualTo null
        }
    }

    @Test
    fun `isDone and uqr_type are not guessed to be historical answer restrictions`() {
        val json = """[{"qc_context":"题", "isDone":"1", "uqr_type":"1"}]"""
        val detail = sut.parseQuestionDetail(json, 1)!!
        detail.isAnswered shouldBeEqualTo false
        detail.hasViewedAnswer shouldBeEqualTo false
    }
}
