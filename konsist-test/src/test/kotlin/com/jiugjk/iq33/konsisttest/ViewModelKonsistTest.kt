package com.jiugjk.iq33.konsisttest

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withoutAllModifiers
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import java.util.Locale
import org.junit.jupiter.api.Test

// Check test coding rules.
//
// Note: see UseCaseKonsistTest for why the "every view model has test" coverage gate was dropped
// in this fork.
class ViewModelKonsistTest {
    @Test
    fun `every view model constructor parameter has name derived from parameter type`() {
        Konsist
            .scopeFromProject()
            .classes()
            .withNameEndingWith("ViewModel")
            .withoutAllModifiers(KoModifier.ABSTRACT)
            .flatMap { it.constructors }
            .flatMap { it.parameters }
            .assertTrue {
                val nameTitleCase = it.name.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
                nameTitleCase == it.type.sourceType
            }
    }
}
