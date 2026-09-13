package com.jiugjk.iq33.app.presentation.library

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jiugjk.iq33.app.R
import com.jiugjk.iq33.feature.base.common.res.Dimen
import com.jiugjk.iq33.feature.favourite.presentation.screen.favourite.FavouriteScreen
import com.jiugjk.iq33.feature.feed.presentation.screen.history.HistoryScreen

private const val PREFS = "library_tab_prefs"
private const val KEY_SEGMENT = "library_segment"
private const val SEGMENT_FAVOURITE = 0
private const val SEGMENT_HISTORY = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onQuestionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var segment by remember { mutableIntStateOf(prefs.getInt(KEY_SEGMENT, SEGMENT_FAVOURITE)) }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimen.spaceL, vertical = Dimen.spaceS),
        ) {
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                onClick = {
                    segment = SEGMENT_FAVOURITE
                    prefs.edit().putInt(KEY_SEGMENT, SEGMENT_FAVOURITE).apply()
                },
                selected = segment == SEGMENT_FAVOURITE,
            ) {
                Text(stringResource(R.string.library_segment_favourite))
            }
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                onClick = {
                    segment = SEGMENT_HISTORY
                    prefs.edit().putInt(KEY_SEGMENT, SEGMENT_HISTORY).apply()
                },
                selected = segment == SEGMENT_HISTORY,
            ) {
                Text(stringResource(R.string.library_segment_history))
            }
        }

        if (segment == SEGMENT_HISTORY) {
            HistoryScreen(onQuestionClick = onQuestionClick)
        } else {
            FavouriteScreen(onQuestionClick = onQuestionClick)
        }
    }
}
