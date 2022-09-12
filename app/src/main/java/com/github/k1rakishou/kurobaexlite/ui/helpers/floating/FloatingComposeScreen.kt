package com.github.k1rakishou.kurobaexlite.ui.helpers.floating

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.launch

abstract class FloatingComposeScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val canDismissByClickingOutside: Boolean = true
) : ComposeScreen(screenArgs, componentActivity, navigationRouter) {
  val backgroundColor = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.6f)

  val horizPaddingDp by lazy { if (globalUiInfoManager.isTablet) HPADDING_TABLET_COMPOSE else HPADDING_COMPOSE }
  val vertPaddingDp by lazy { if (globalUiInfoManager.isTablet) VPADDING_TABLET_COMPOSE else VPADDING_COMPOSE }

  open val contentAlignment: Alignment = Alignment.Center

  open val presentAnimation: NavigationRouter.ScreenAnimation
    get() {
      return NavigationRouter.ScreenAnimation.Fade(
        fadeType = NavigationRouter.ScreenAnimation.FadeType.In,
        screenKey = screenKey
      )
    }

  open val unpresentAnimation: NavigationRouter.ScreenAnimation
    get() {
      return NavigationRouter.ScreenAnimation.Fade(
        fadeType = NavigationRouter.ScreenAnimation.FadeType.Out,
        screenKey = screenKey
      )
    }

  protected val touchPositionDependantAlignment by lazy {
    TouchPositionDependantAlignment(
      lastTouchPositionX = globalUiInfoManager.lastTouchPosition.x.toFloat(),
      lastTouchPositionY = globalUiInfoManager.lastTouchPosition.y.toFloat(),
    )
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  override fun Content() {
    val localFocusManager = LocalFocusManager.current
    val localSoftwareKeyboardController = LocalSoftwareKeyboardController.current

    DefaultFloatingScreenBackPressHandler()

    // Make sure the keyboard is getting closed when a floating screen is destroyed
    DisposableEffect(
      key1 = Unit,
      effect = {
        onDispose {
          localFocusManager.clearFocus(force = true)
          localSoftwareKeyboardController?.hide()
        }
      }
    )

    BackgroundContent()
  }

  @Composable
  open fun DefaultFloatingScreenBackPressHandler() {
    HandleBackPresses { stopPresenting() }
  }

  @Composable
  open fun BackgroundContent() {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .consumeClicks(enabled = true)
    ) {
      CardContent()
    }
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
          onClick = {
            if (canDismissByClickingOutside) {
              coroutineScope.launch { onBackPressed() }
            }
          }
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
        contentAlignment = remember { contentAlignment },
      ) {
        val maxWidthDp = maxAvailableWidth()
        val maxHeightDp = maxAvailableHeight()

        KurobaComposeCard(
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
    val isTablet = globalUiInfoManager.isTablet
    val maxParentHeight by globalUiInfoManager.totalScreenHeightState.collectAsState()

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
    val isTablet = globalUiInfoManager.isTablet
    val maxParentWidth by globalUiInfoManager.totalScreenHeightState.collectAsState()

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

  protected fun stopPresenting(): Boolean {
    return navigationRouter.stopPresentingScreen(screenKey)
  }

  protected class TouchPositionDependantAlignment(
    private val lastTouchPositionX: Float,
    private val lastTouchPositionY: Float,
  ) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
      val availableWidth = space.width
      val availableHeight = space.height

      if (availableWidth <= 0 || availableHeight <= 0 || size.width <= 0 || size.height <= 0) {
        return IntOffset.Zero
      }

      val biasX = (lastTouchPositionX / availableWidth.toFloat()).coerceIn(0f, 1f)
      val biasY = (lastTouchPositionY / availableHeight.toFloat()).coerceIn(0f, 1f)

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