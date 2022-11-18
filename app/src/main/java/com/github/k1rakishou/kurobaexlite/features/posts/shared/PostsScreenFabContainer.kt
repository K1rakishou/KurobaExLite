package com.github.k1rakishou.kurobaexlite.features.posts.shared

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.thread.PostFollowStack
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaFloatingActionButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.passClicksThrough

const val FAB_TRANSITION_ANIMATION_DURATION_MS = 200

@Composable
fun BoxScope.PostsScreenFabContainer(
  screenKey: ScreenKey,
  screenContentLoaded: Boolean,
  lastLoadedEndedWithError: Boolean,
  mainUiLayoutMode: MainUiLayoutMode,
  onGoBackFabClicked: () -> Unit,
  onReplyFabClicked: (ScreenKey) -> Unit,
) {
  if (mainUiLayoutMode != MainUiLayoutMode.Split) {
    return
  }

  val insets = LocalWindowInsets.current

  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val snackbarManager = koinRemember<SnackbarManager>()
  val postFollowStack = koinRemember<PostFollowStack>()

  val hideableUiVisibilityInfo = remember(key1 = screenKey) {
    globalUiInfoManager.getOrCreateHideableUiVisibilityInfo(screenKey)
  }

  var activeSnackbarsCount by remember { mutableStateOf(0) }
  val screensUsingSearch by hideableUiVisibilityInfo.childScreensUsingSearch.collectAsState()
  val replyLayoutOpened by hideableUiVisibilityInfo.replyLayoutOpened.collectAsState()

  LaunchedEffect(
    key1 = screenKey,
    block = {
      snackbarManager
        .listenForActiveSnackbarsFlow(screenKey)
        .collect { activeSnackbars -> activeSnackbarsCount = activeSnackbars.size }
    }
  )

  val combinedFabState by remember(screenKey, screenContentLoaded, lastLoadedEndedWithError) {
    derivedStateOf {
      CombinedFabState(
        activeSnackbarsCount = activeSnackbarsCount,
        screenContentLoaded = screenContentLoaded,
        replyLayoutOpened = replyLayoutOpened,
        lastLoadedEndedWithError = lastLoadedEndedWithError,
        screensUsingSearch = screensUsingSearch
      )
    }
  }

  val transition = updateTransition(
    targetState = combinedFabState,
    label = "fab transition"
  )

  val toolbarAlphaState = transition.animateFloat(
    label = "fab alpha animation",
    transitionSpec = { tween(durationMillis = FAB_TRANSITION_ANIMATION_DURATION_MS) },
    targetValueByState = { state ->
      when {
        !state.screenContentLoaded -> 0f
        state.activeSnackbarsCount > 0 -> 0f
        state.replyLayoutOpened -> 0f
        state.lastLoadedEndedWithError -> 0f
        state.screensUsingSearch.isNotEmpty() -> 0f
        else -> 1f
      }
    }
  )

  val horizOffset = dimensionResource(id = R.dimen.post_list_fab_end_offset)
  val vertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)
  val passClicks by remember { derivedStateOf { toolbarAlphaState.value < 0.99f } }

  Column(
    modifier = Modifier
      .align(Alignment.BottomEnd)
      .offset { IntOffset(x = -(horizOffset.roundToPx()), y = -(insets.bottom + vertOffset).roundToPx()) }
  ) {
    if (screenKey == ThreadScreen.SCREEN_KEY) {
      val isHistoryEmpty by postFollowStack.isHistoryEmpty
      if (!isHistoryEmpty) {
        KurobaFloatingActionButton(
          modifier = Modifier
            .graphicsLayer { this.alpha = toolbarAlphaState.value }
            .passClicksThrough(passClicks = passClicks),
          iconDrawableId = R.drawable.ic_baseline_arrow_back_24,
          backgroundColor = Color.White,
          onClick = { onGoBackFabClicked() }
        )

        Spacer(modifier = Modifier.height(24.dp))
      }
    }

    KurobaFloatingActionButton(
      modifier = Modifier
        .graphicsLayer { this.alpha = toolbarAlphaState.value }
        .passClicksThrough(passClicks = passClicks),
      iconDrawableId = R.drawable.ic_baseline_create_24,
      onClick = { onReplyFabClicked(screenKey) }
    )
  }
}

private data class CombinedFabState(
  val activeSnackbarsCount: Int,
  val screenContentLoaded: Boolean,
  val replyLayoutOpened: Boolean,
  val lastLoadedEndedWithError: Boolean,
  val screensUsingSearch: Set<ScreenKey>
)