package com.jiugjk.iq33.feature.feed.presentation.screen.imageviewer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.feed.R
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Full-screen, immersive viewer for a question's images.
 *
 * A [Dialog] with `usePlatformDefaultWidth = false` and `decorFitsSystemWindows = false` is what
 * makes this cover the whole screen including under the system bars. It also gives back-button
 * dismissal for free - including the predictive-back animation once the activity has opted in -
 * which is what a hand-rolled overlay would have to reimplement.
 *
 * @param imageUrls every image in the question, so the pager can move between them.
 * @param initialIndex the image that was tapped.
 * @param onDismiss closes the viewer. Bound to the system back button and a tap at 1x zoom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageViewerDialog(
    imageUrls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (imageUrls.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(imageUrls.indices)) { imageUrls.size }
    val snackbarHostState = remember { SnackbarHostState() }
    var sheetOpen by remember { mutableStateOf(false) }
    val requestSave = rememberImageSaveRequest(imageUrls = imageUrls, pagerState = pagerState, snackbarHostState = snackbarHostState)

    ImageViewerBody(
        imageUrls = imageUrls,
        pagerState = pagerState,
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss,
        onLongPress = { sheetOpen = true },
    )

    if (sheetOpen) {
        ImageSaveSheet(
            onDismissRequest = { sheetOpen = false },
            onSaveClick = {
                sheetOpen = false
                requestSave()
            },
        )
    }
}

@Composable
private fun ImageViewerBody(
    imageUrls: List<String>,
    pagerState: PagerState,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onLongPress: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Opaque black rather than a scrim: this is a photo viewer, and a translucent
                    // backdrop would leave the question text competing with the image.
                    .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    imageUrl = imageUrls[page],
                    contentDescription = stringResource(R.string.feed_question_image_content_description),
                    onTap = onDismiss,
                    onLongPress = onLongPress,
                )
            }

            ViewerTopBar(
                pageLabel =
                    if (imageUrls.size > 1) {
                        stringResource(R.string.feed_image_page_indicator, pagerState.currentPage + 1, imageUrls.size)
                    } else {
                        null
                    },
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            ViewerSnackbar(
                snackbarHostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun rememberImageSaveRequest(
    imageUrls: List<String>,
    pagerState: PagerState,
    snackbarHostState: SnackbarHostState,
): () -> Unit {
    val imageSaver: ImageSaver = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.feed_image_saved)
    val saveFailedMessage = stringResource(R.string.feed_image_save_failed)
    val permissionDeniedMessage = stringResource(R.string.feed_image_save_permission_denied)

    fun saveCurrentImage() {
        val url = imageUrls[pagerState.currentPage]

        scope.launch {
            val savedName = imageSaver.saveToGallery(context, url)

            snackbarHostState.showSnackbar(if (savedName != null) savedMessage else saveFailedMessage)
        }
    }

    // Below Android 10 there is no scoped storage, so writing to the shared gallery needs the
    // legacy permission. minSdk is 28, so this covers exactly API 28.
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                saveCurrentImage()
            } else {
                scope.launch { snackbarHostState.showSnackbar(permissionDeniedMessage) }
            }
        }

    return {
        if (needsLegacyStoragePermission(context)) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveCurrentImage()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageSaveSheet(
    onDismissRequest: () -> Unit,
    onSaveClick: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.feed_image_save_to_gallery)) },
            leadingContent = {
                Icon(imageVector = Icons.Default.Download, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSaveClick)
                    .navigationBarsPadding(),
        )
    }
}

@Composable
private fun ViewerTopBar(
    pageLabel: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(Dimen.spaceM),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.feed_image_close),
                tint = Color.White,
            )
        }

        if (pageLabel != null) {
            Text(
                text = pageLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ViewerSnackbar(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier.navigationBarsPadding().padding(Dimen.spaceL),
    )
}

private fun needsLegacyStoragePermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false

    return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
}
