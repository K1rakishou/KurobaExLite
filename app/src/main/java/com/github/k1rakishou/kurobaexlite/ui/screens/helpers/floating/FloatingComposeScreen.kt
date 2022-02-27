package com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating

import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import kotlinx.coroutines.launch

abstract class FloatingComposeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  val horizPaddingDp by lazy {
    if (uiInfoManager.isTablet) {
      HPADDING_TABLET_COMPOSE
    } else {
      HPADDING_COMPOSE
    }
  }

  val vertPaddingDp by lazy {
    if (uiInfoManager.isTablet) {
      VPADDING_TABLET_COMPOSE
    } else {
      VPADDING_COMPOSE
    }
  }

  val horizPaddingPx by lazy { with(uiInfoManager.composeDensity) { horizPaddingDp.toPx() } }
  val vertPaddingPx by lazy { with(uiInfoManager.composeDensity) { vertPaddingDp.toPx() } }

  open val contentAlignment: Alignment = Alignment.Center

  @Composable
  override fun Content() {
    val insets = LocalWindowInsets.current
    val coroutineScope = rememberCoroutineScope()

    HandleBackPresses()

    Box(
      modifier = Modifier
        .fillMaxSize()
        .kurobaClickable(
          hasClickIndication = false,
          onClick = { coroutineScope.launch { onBackPressed() } }
        )
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(
            start = insets.left + horizPaddingDp,
            end = insets.right + horizPaddingDp,
            top = insets.top + vertPaddingDp,
            bottom = insets.bottom + vertPaddingDp,
          ),
        contentAlignment = contentAlignment,
      ) {
        val maxWidthDp = maxAvailableWidth()
        val maxHeightDp = maxAvailableHeight()

        KurobaComposeCardView(
          modifier = Modifier
            .wrapContentSize()
            .widthIn(max = maxWidthDp)
            .heightIn(max = maxHeightDp)
            .consumeClicks()
        ) {
          FloatingContent()
        }
      }
    }
  }

  @Composable
  open fun maxAvailableHeight(): Dp {
    val isTablet = uiInfoManager.isTablet

    return with(LocalDensity.current) {
      return@with remember(key1 = this, key2 = isTablet) {
        val maxHeight = if (isTablet) {
          uiInfoManager.maxParentHeight - (uiInfoManager.maxParentHeight / 4)
        } else {
          uiInfoManager.maxParentHeight
        }

        return@remember maxHeight.toDp()
      }
    }
  }

  @Composable
  fun maxAvailableHeightPx(): Float {
    return with(LocalDensity.current) { maxAvailableHeight().toPx() }
  }

  @Composable
  open fun maxAvailableWidth(): Dp {
    val isTablet = uiInfoManager.isTablet

    return with(LocalDensity.current) {
      return@with remember(key1 = this, key2 = isTablet) {
        val maxWidth = if (isTablet) {
          uiInfoManager.maxParentWidth - (uiInfoManager.maxParentWidth / 4)
        } else {
          uiInfoManager.maxParentWidth
        }

        return@remember maxWidth.toDp()
      }
    }
  }

  @Composable
  fun maxAvailableWidthPx(): Float {
    return with(LocalDensity.current) { maxAvailableWidth().toPx() }
  }

  @Composable
  private fun HandleBackPresses() {
    DisposableEffect(
      key1 = Unit,
      effect = {
        val handler = object : NavigationRouter.OnBackPressHandler {
          override suspend fun onBackPressed(): Boolean {
            return this@FloatingComposeScreen.onBackPressed()
          }
        }

        navigationRouter.addOnBackPressedHandler(handler)

        onDispose {
          navigationRouter.removeOnBackPressedHandler(handler)
        }
      }
    )
  }

  @Composable
  open fun FloatingContent() {

  }

  @CallSuper
  open suspend fun onBackPressed(): Boolean {
    return stopPresenting()
  }

  open fun onDestroy() {

  }

  protected fun stopPresenting(): Boolean {
    val success = navigationRouter.stopPresentingScreen(screenKey = screenKey)
    if (success) {
      onDestroy()
    }

    return success
  }

  companion object {
    val HPADDING_COMPOSE = 12.dp
    val VPADDING_COMPOSE = 16.dp

    val HPADDING_TABLET_COMPOSE = 32.dp
    val VPADDING_TABLET_COMPOSE = 48.dp
  }

}