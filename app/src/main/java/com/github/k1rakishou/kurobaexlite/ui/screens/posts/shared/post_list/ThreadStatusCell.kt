package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal val threadStatusCellKey = "thread_status_cell"

@Composable
internal fun LazyItemScope.ThreadStatusCell(
  padding: PaddingValues,
  lazyListState: LazyListState,
  threadScreenViewModel: ThreadScreenViewModel,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val threadStatusCellDataFromState by threadScreenViewModel.postScreenState.threadCellDataState.collectAsState()
  val chanDescriptor = threadScreenViewModel.postScreenState.chanDescriptor

  val threadStatusCellDataUpdatedMut by rememberUpdatedState(newValue = threadStatusCellDataFromState)
  val threadStatusCellDataUpdated = threadStatusCellDataUpdatedMut

  if (threadStatusCellDataUpdated == null || (chanDescriptor == null || chanDescriptor !is ThreadDescriptor)) {
    Spacer(modifier = Modifier.height(Dp.Hairline))
    return
  }

  val fabSize = dimensionResource(id = R.dimen.fab_size)
  val fabEndOffset = dimensionResource(id = R.dimen.post_list_fab_end_offset)

  val coroutineScope = rememberCoroutineScope()
  val lastItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index

  DisposableEffect(
    key1 = lastItemIndex,
    effect = {
      val job = coroutineScope.launch {
        delay(125L)

        val threadStatusCellItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.key == threadStatusCellKey
        if (threadStatusCellItem) {
          threadScreenViewModel.onPostListTouchingBottom()
        }
      }

      onDispose {
        job.cancel()

        val threadStatusCellItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.key == threadStatusCellKey
        if (!threadStatusCellItem) {
          threadScreenViewModel.onPostListNotTouchingBottom()
        }
      }
    })

  var timeUntilNextUpdateSeconds by remember { mutableStateOf(0L) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      while (isActive) {
        delay(1000L)
        timeUntilNextUpdateSeconds = threadScreenViewModel.timeUntilNextUpdateMs / 1000L
      }
    })

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .kurobaClickable(
        onClick = {
          if (threadStatusCellDataUpdated.canRefresh()) {
            onThreadStatusCellClicked(chanDescriptor)
          }
        }
      )
  ) {
    val context = LocalContext.current

    val threadStatusCellText = remember(key1 = threadStatusCellDataUpdated, key2 = timeUntilNextUpdateSeconds) {
      buildAnnotatedString {
        if (threadStatusCellDataUpdated.totalReplies > 0) {
          append(threadStatusCellDataUpdated.totalReplies.toString())
          append("R")
        }

        if (threadStatusCellDataUpdated.totalImages > 0) {
          if (length > 0) {
            append(", ")
          }

          append(threadStatusCellDataUpdated.totalImages.toString())
          append("I")
        }

        if (threadStatusCellDataUpdated.totalPosters > 0) {
          if (length > 0) {
            append(", ")
          }

          append(threadStatusCellDataUpdated.totalPosters.toString())
          append("P")
        }

        if (threadStatusCellDataUpdated.bumpLimit == true) {
          if (length > 0) {
            append(", ")
          }

          append("BL")
        }

        if (threadStatusCellDataUpdated.imageLimit == true) {
          if (length > 0) {
            append(", ")
          }

          append("IL")
        }

        val threadStatusText = buildAnnotatedString {
          if (threadStatusCellDataUpdated.archived == true) {
            if (length > 0) {
              append(", ")
            }

            append(context.getString(R.string.thread_status_archived))
          }

          if (threadStatusCellDataUpdated.closed == true) {
            if (length > 0) {
              append(", ")
            }

            append(context.getString(R.string.thread_status_closed))
          }

          if (threadStatusCellDataUpdated.sticky == true) {
            if (length > 0) {
              append(", ")
            }

            append(context.getString(R.string.thread_status_pinned))
          }
        }

        if (threadStatusText.isNotEmpty()) {
          append("\n")
          append(threadStatusText)
        }

        if (threadStatusCellDataUpdated.lastLoadError != null) {
          val lastLoadErrorText = threadStatusCellDataUpdated.errorMessage(context)

          append("\n")
          append(lastLoadErrorText)
          append("\n")
          append(context.resources.getString(R.string.thread_load_failed_tap_to_refresh))
        } else if (threadStatusCellDataUpdated.canRefresh()) {
          append("\n")

          val loadingText = if (timeUntilNextUpdateSeconds > 0L) {
            context.resources.getString(
              R.string.thread_screen_status_cell_loading_in,
              timeUntilNextUpdateSeconds
            )
          } else {
            context.resources.getString(R.string.thread_screen_status_cell_loading_right_now)
          }

          append(loadingText)
        }
      }
    }

    val combinedPaddings = remember(key1 = threadStatusCellDataUpdated.lastLoadError) {
      val endPadding = if (threadStatusCellDataUpdated.lastLoadError != null) {
        fabSize + fabEndOffset
      } else {
        0.dp
      }

      PaddingValues(
        start = padding.calculateStartPadding(LayoutDirection.Ltr),
        end = padding.calculateEndPadding(LayoutDirection.Ltr) + endPadding,
        top = 16.dp,
        bottom = 16.dp
      )
    }

    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(combinedPaddings)
        .align(Alignment.Center),
      text = threadStatusCellText,
      color = chanTheme.textColorSecondaryCompose,
      textAlign = TextAlign.Center
    )
  }
}
