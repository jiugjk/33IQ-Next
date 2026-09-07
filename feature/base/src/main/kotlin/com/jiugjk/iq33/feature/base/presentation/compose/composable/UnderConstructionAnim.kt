package com.jiugjk.iq33.feature.base.presentation.compose.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.jiugjk.iq33.feature.base.R

@Composable
fun UnderConstructionAnim() {
    LabeledAnimation(R.string.common_under_construction, R.raw.lottie_building_screen)
}

@Preview
@Composable
private fun UnderConstructionAnimPreview() {
    UnderConstructionAnim()
}
