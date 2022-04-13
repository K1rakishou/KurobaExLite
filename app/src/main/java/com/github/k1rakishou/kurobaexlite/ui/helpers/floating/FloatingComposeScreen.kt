package com.github.k1rakishou.kurobaexlite.ui.helpers.floating

import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCardView
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.launch

abstract class FloatingComposeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  val horizPaddingDp by lazy { if (uiInfoManager.isTablet) HPADDING_TABLET_COMPOSE else HPADDING_COMPOSE }
  val vertPaddingDp by lazy { if (uiInfoManager.isTablet) VPADDING_TABLET_COMPOSE else VPADDING_COMPOSE }

  open val contentAlignment: Alignment = Alignment.Center
  open val presentAnimation: NavigationRouter.ScreenAddAnimation = NavigationRouter.ScreenAddAnimation.FadeIn
  open val unpresentAnimation: NavigationRouter.ScreenRemoveAnimation = NavigationRouter.ScreenRemoveAnimation.FadeOut

  protected val touchPositionDependantAlignment by lazy { TouchPositionDependantAlignment(uiInfoManager) }

  @Composable
  override fun Content() {
    HandleBackPresses { onFloatingControllerBackPressed() }

    // However when it comes to screen destroy events, we need use the MainScreen's router since that's
    // where all the floating screens are being added to.
    val mainRouter = remember { navigationRouter.getRouterByKey(MainScreen.SCREEN_KEY) }
    mainRouter.HandleScreenDestroyEvents(screenKey = screenKey) { onDestroy() }

    CardContent()
  }

  @Composable
  open fun CardContent() {
    val insets = LocalWindowInsets.current
    val coroutineScope = rememberCoroutineScope()

    Box(
      modifier = Modifier
        .fillMaxSize()
        .kurobaClickable(
          hasClickIndication = false,
          onClick = { coroutineScope.launch { onFloatingControllerBackPressed() } }
        )
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(
            // Left and right paddings are set globally in MainScreen
            start = horizPaddingDp,
            end = horizPaddingDp,
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
  open fun FloatingContent() {

  }

  @Composable
  open fun maxAvailableHeight(): Dp {
    val isTablet = uiInfoManager.isTablet
    val maxParentHeight by uiInfoManager.totalScreenHeightState

    return with(LocalDensity.current) {
      return@with remember(key1 = this, key2 = isTablet) {
        val maxHeight = if (isTablet) {
          maxParentHeight - (maxParentHeight / 4)
        } else {
          maxParentHeight
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
    val maxParentWidth by uiInfoManager.totalScreenHeightState

    return with(LocalDensity.current) {
      return@with remember(key1 = this, key2 = isTablet) {
        val maxWidth = if (isTablet) {
          maxParentWidth - (maxParentWidth / 4)
        } else {
          maxParentWidth
        }

        return@remember maxWidth.toDp()
      }
    }
  }

  @Composable
  fun maxAvailableWidthPx(): Float {
    return with(LocalDensity.current) { maxAvailableWidth().toPx() }
  }

  @CallSuper
  open suspend fun onFloatingControllerBackPressed(): Boolean {
    return stopPresenting()
  }

  open fun onDestroy() {

  }

  protected fun stopPresenting(): Boolean {
    return navigationRouter.stopPresentingScreen(
      screenKey = screenKey,
      overrideAnimation = unpresentAnimation
    )
  }

  protected class TouchPositionDependantAlignment(
    private val uiInfoManager: UiInfoManager
  ) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
      val availableWidth = space.width
      val availableHeight = space.height

      if (availableWidth <= 0 || availableHeight <= 0 || size.width <= 0 || size.height <= 0) {
        return IntOffset.Zero
      }

      val biasX = (uiInfoManager.lastTouchPosition.x.toFloat() / availableWidth.toFloat()).coerceIn(0f, 1f)
      val biasY = (uiInfoManager.lastTouchPosition.y.toFloat() / availableHeight.toFloat()).coerceIn(0f, 1f)

      val offsetX = ((availableWidth - (size.width)).toFloat() * biasX).toInt()
      val offsetY = ((availableHeight - (size.height)).toFloat() * biasY).toInt()

      return IntOffset(x = offsetX, y = offsetY)
    }
  }

  companion object {
    val HPADDING_COMPOSE = 12.dp
    val VPADDING_COMPOSE = 16.dp

    val HPADDING_TABLET_COMPOSE = 32.dp
    val VPADDING_TABLET_COMPOSE = 48.dp
  }

}