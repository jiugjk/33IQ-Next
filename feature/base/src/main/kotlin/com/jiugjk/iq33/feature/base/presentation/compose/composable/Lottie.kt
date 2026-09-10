package com.jiugjk.iq33.feature.base.presentation.compose.composable

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.jiugjk.iq33.feature.base.common.res.Dimen

@Composable
fun LottieAssetLoader(
    @RawRes assetResId: Int,
    modifier: Modifier = Modifier,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(assetResId))

    LottieAnimation(
        composition,
        modifier = modifier.requiredSize(Dimen.imageSize),
    )
}

@Preview
@Composable
private fun LottieAssetLoaderPreview() {
    LottieAssetLoader(
        assetResId = com.jiugjk.iq33.feature.base.R.raw.lottie_error_screen,
    )
}
