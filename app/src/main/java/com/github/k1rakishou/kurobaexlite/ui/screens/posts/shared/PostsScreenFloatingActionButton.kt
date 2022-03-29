package com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaFloatingActionButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.home.HomeScreenViewModel
import kotlinx.coroutines.flow.StateFlow

const val FAB_TRANSITION_ANIMATION_DURATION_MS = 200

@OptIn(ExperimentalPagerApi::class)
@Composable
fun BoxScope.PostsScreenFloatingActionButton(
  screenKey: ScreenKey,
  screenContentLoadedFlow: StateFlow<Boolean>,
  mainUiLayoutMode: MainUiLayoutMode,
  homeScreenViewModel: HomeScreenViewModel,
  snackbarManager: SnackbarManager
) {
  if (mainUiLayoutMode != MainUiLayoutMode.Split) {
    return
  }

  val toolbarVisibilityInfo = homeScreenViewModel.getOrCreateToolbarVisibilityInfo(screenKey)
  val insets = LocalWindowInsets.current

  var activeSnackbarsCount by remember { mutableStateOf(0) }
  val screensUsingSearch by toolbarVisibilityInfo.childScreensUsingSearch.collectAsState()
  val screenContentLoaded by screenContentLoadedFlow.collectAsState()

  LaunchedEffect(
    key1 = screenKey,
    block = {
      snackbarManager
        .listenForActiveSnackbarsFlow(screenKey)
        .collect { activeSnackbars -> activeSnackbarsCount = activeSnackbars.size }
    })

  val combinedFabState by remember(key1 = screenKey) {
    derivedStateOf {
      CombinedFabState(
        activeSnackbarsCount = activeSnackbarsCount,
        screenContentLoaded = screenContentLoaded,
        screensUsingSearch = screensUsingSearch
      )
    }
  }

  val transition = updateTransition(
    targetState = combinedFabState,
    label = "fab transition"
  )

  val toolbarAlpha by transition.animateFloat(
    label = "fab alpha animation",
    transitionSpec = { tween(durationMillis = FAB_TRANSITION_ANIMATION_DURATION_MS) },
    targetValueByState = { state ->
      when {
        !state.screenContentLoaded -> 0f
        state.activeSnackbarsCount > 0 -> 0f
        state.screensUsingSearch.isNotEmpty() -> 0f
        else -> 1f
      }
    }
  )

  val horizOffset = dimensionResource(id = R.dimen.post_list_fab_end_offset)
  val vertOffset = dimensionResource(id = R.dimen.post_list_fab_bottom_offset)

  KurobaFloatingActionButton(
    modifier = Modifier
      .align(Alignment.BottomEnd)
      .alpha(toolbarAlpha),
    iconDrawableId = R.drawable.ic_baseline_create_24,
    horizOffset = -(horizOffset),
    vertOffset = -(insets.bottom + vertOffset),
    onClick = {
      if (toolbarAlpha <= 0.99f) {
        return@KurobaFloatingActionButton
      }

      homeScreenViewModel.onFabClicked(screenKey)
    }
  )
}

private data class CombinedFabState(
  val activeSnackbarsCount: Int,
  val screenContentLoaded: Boolean,
  val screensUsingSearch: Set<ScreenKey>
)