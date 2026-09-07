package com.jiugjk.iq33.feature.base.presentation.compose.composable

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jiugjk.iq33.feature.base.R

private val PLACEHOLDER_IMAGES =
    listOf(
        R.drawable.image_placeholder_1,
        R.drawable.image_placeholder_2,
        R.drawable.image_placeholder_3,
    )

@Composable
fun PlaceholderImage(
    url: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier) {
        val randomPlaceHolder by rememberSaveable {
            mutableStateOf(PLACEHOLDER_IMAGES.random())
        }

        val model =
            ImageRequest
                .Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build()

        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            placeholder = painterResource(randomPlaceHolder),
        )
    }
}

@Preview
@Composable
private fun PlaceholderImagePreview() {
    PlaceholderImage(
        url = "https://a.33iq.com/images/favicon.ico",
        contentDescription = "Sample image",
    )
}
