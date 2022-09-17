package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal val threadStatusCellKey = "thread_status_cell"

@Composable
internal fun ThreadStatusCell(
  padding: PaddingValues,
  threadScreenViewModelProvider: () -> ThreadScreenViewModel,
  onThreadStatusCellClicked: (ThreadDescriptor) -> Unit
) {
  val chanTheme = LocalChanTheme.current

  val threadScreenViewModel = threadScreenViewModelProvider()
  val threadStatusCellDataFromState by threadScreenViewModel.postScreenState.threadCellDataState.collectAsState()
  val lastLoadError by threadScreenViewModel.postScreenState.lastLoadErrorState.collectAsState()
  val chanDescriptor = threadScreenViewModel.postScreenState.chanDescriptor

  val threadStatusCellDataUpdatedMut by rememberUpdatedState(newValue = threadStatusCellDataFromState)
  val threadStatusCellDataUpdated = threadStatusCellDataUpdatedMut

  if (threadStatusCellDataUpdated == null || (chanDescriptor == null || chanDescriptor !is ThreadDescriptor)) {
    Spacer(modifier = Modifier.height(Dp.Hairline))
    return
  }

  var timeUntilNextUpdateSeconds by rememberSaveable(key = "time_until_next_update_seconds") { mutableStateOf(0L) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      while (isActive) {
        timeUntilNextUpdateSeconds = threadScreenViewModel.timeUntilNextUpdateMs / 1000L
        delay(1000L)
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

    val threadStatusCellText = remember(
      key1 = threadStatusCellDataUpdated,
      key2 = timeUntilNextUpdateSeconds,
      key3 = lastLoadError
    ) {
      val isThreadDeleted = threadStatusCellDataUpdated.isThreadDeleted(lastLoadError)

      buildAnnotatedString {
        if (threadStatusCellDataUpdated.totalReplies > 0) {
          append(threadStatusCellDataUpdated.totalReplies.toString())
          append("R")
        }

        if (threadStatusCellDataUpdated.totalImages > 0) {
          if (length > 0) {
            append(AppConstants.TEXT_SEPARATOR)
          }

          append(threadStatusCellDataUpdated.totalImages.toString())
          append("I")
        }

        if (threadStatusCellDataUpdated.totalPosters > 0) {
          if (length > 0) {
            append(AppConstants.TEXT_SEPARATOR)
          }

          append(threadStatusCellDataUpdated.totalPosters.toString())
          append("P")
        }

        if (!isThreadDeleted && threadStatusCellDataUpdated.threadPage != null) {
          if (length > 0) {
            append(AppConstants.TEXT_SEPARATOR)
          }

          append("Pg: ")
          append(threadStatusCellDataUpdated.threadPage.page.toString())
          append("/")
          append(threadStatusCellDataUpdated.threadPage.totalPages.toString())
        }

        if (threadStatusCellDataUpdated.bumpLimit == true) {
          if (length > 0) {
            append(AppConstants.TEXT_SEPARATOR)
          }

          append("BL")
        }

        if (threadStatusCellDataUpdated.imageLimit == true) {
          if (length > 0) {
            append(AppConstants.TEXT_SEPARATOR)
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

          if (threadStatusCellDataUpdated.sticky != null) {
            if (length > 0) {
              append(", ")
            }

            val capacity = threadStatusCellDataUpdated.sticky.capacity
            if (capacity != null && capacity > 0) {
              append(context.getString(R.string.thread_status_pinned_with_cap, capacity))
            } else {
              append(context.getString(R.string.thread_status_pinned))
            }
          }
        }

        if (threadStatusText.isNotEmpty()) {
          append("\n")
          append(threadStatusText)
        }

        if (lastLoadError != null) {
          val lastLoadErrorText = threadStatusCellDataUpdated.errorMessage(context, lastLoadError)

          append("\n")
          append(lastLoadErrorText)

          if (!isThreadDeleted) {
            append("\n")
            append(context.resources.getString(R.string.thread_load_failed_tap_to_refresh))
          }
        } else if (threadStatusCellDataUpdated.canRefresh()) {
          append("\n")

          val loadingText = if (timeUntilNextUpdateSeconds > 0L) {
            context.resources.getString(
              R.string.thread_toolbar_status_cell_loading_in,
              timeUntilNextUpdateSeconds
            )
          } else {
            context.resources.getString(R.string.thread_toolbar_status_cell_loading_right_now)
          }

          append(loadingText)
        }
      }
    }

    val combinedPaddings = remember {
      PaddingValues(
        start = padding.calculateStartPadding(LayoutDirection.Ltr),
        end = padding.calculateEndPadding(LayoutDirection.Ltr),
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
      color = chanTheme.textColorSecondary,
      textAlign = TextAlign.Center
    )
  }
}
