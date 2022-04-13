package com.github.k1rakishou.kurobaexlite.features.drawer

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalComponentActivity
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext


private val COLOR_INVISIBLE = Color(0x00000000)
private val COLOR_VISIBLE = Color(0x80000000)

@Composable
fun HomeScreenDrawerLayout(
  drawerWidth: Int,
  navigationRouter: NavigationRouter,
) {
  val componentActivity = LocalComponentActivity.current
  val uiInfoManager = GlobalContext.get().get<UiInfoManager>()

  val childRouter = remember { navigationRouter.childRouter(DrawerScreen.SCREEN_KEY) }
  val drawerScreen = remember { DrawerScreen(componentActivity, navigationRouter) }
  val drawerVisibility by uiInfoManager.drawerVisibilityFlow.collectAsState()
  val maxOffsetDp = with(LocalDensity.current) { remember(key1 = drawerWidth) { -drawerWidth.toDp() } }
  val drawerWidthDp = with(LocalDensity.current) { remember(key1 = drawerWidth) { drawerWidth.toDp() } }

  val transition = updateTransition(
    targetState = drawerVisibility,
    label = "Drawer transition"
  )

  val offsetAnimated by transition.animateDp(
    label = "Offset animation",
    transitionSpec = {
      if (
        targetState is DrawerVisibility.Closing
        || targetState is DrawerVisibility.Opening
      ) {
        tween(durationMillis = 250)
      } else {
        snap()
      }
    },
    targetValueByState = { dv ->
      return@animateDp when (dv) {
        DrawerVisibility.Closing -> maxOffsetDp
        DrawerVisibility.Opening -> 0.dp
        is DrawerVisibility.Drag -> maxOffsetDp * dv.progressInverted
        DrawerVisibility.Closed -> maxOffsetDp
        DrawerVisibility.Opened -> 0.dp
      }
    }
  )

  val backgroundAnimated by transition.animateColor(
    label = "Color animation",
    transitionSpec = {
      if (
        targetState is DrawerVisibility.Closing
        || targetState is DrawerVisibility.Opening
      ) {
        tween(durationMillis = 250)
      } else {
        snap()
      }
    },
    targetValueByState = { dv ->
      return@animateColor when (dv) {
        DrawerVisibility.Closing -> COLOR_INVISIBLE
        DrawerVisibility.Opening -> COLOR_VISIBLE
        is DrawerVisibility.Drag -> {
          COLOR_VISIBLE.copy(alpha = COLOR_VISIBLE.alpha * dv.progress)
        }
        DrawerVisibility.Closed -> COLOR_INVISIBLE
        DrawerVisibility.Opened -> COLOR_VISIBLE
      }
    }
  )

  val clickable = remember(key1 = drawerVisibility) {
    when (drawerVisibility) {
      DrawerVisibility.Closed -> false
      DrawerVisibility.Closing,
      is DrawerVisibility.Drag,
      DrawerVisibility.Opened,
      DrawerVisibility.Opening -> true
    }
  }

  val clickableModifier = remember(key1 = clickable) {
    if (clickable) {
      Modifier.kurobaClickable(hasClickIndication = false) { uiInfoManager.closeDrawer() }
    } else {
      Modifier
    }
  }

  val isAnimationRunning = if (drawerVisibility is DrawerVisibility.Drag) {
    (drawerVisibility as DrawerVisibility.Drag).isDragging
  } else {
    transition.isRunning
  }

  if (!isAnimationRunning) {
    val velocityEnoughToAnimate = 7000f

    LaunchedEffect(
      key1 = drawerVisibility,
      block = {
        // debouncing
        delay(50)

        when (val dv = drawerVisibility) {
          DrawerVisibility.Closing -> {
            uiInfoManager.closeDrawer(withAnimation = false)
          }
          DrawerVisibility.Opening -> {
            uiInfoManager.openDrawer(withAnimation = false)
          }
          is DrawerVisibility.Drag -> {
            when {
              dv.velocity > velocityEnoughToAnimate -> uiInfoManager.openDrawer()
              dv.velocity < -velocityEnoughToAnimate -> uiInfoManager.closeDrawer()
              else -> {
                if (dv.progress > 0.5f) {
                  uiInfoManager.openDrawer()
                } else {
                  uiInfoManager.closeDrawer()
                }
              }
            }
          }
          else -> {
            // no-op
          }
        }
      })
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(backgroundAnimated)
      .then(clickableModifier)
  ) {
    Box(
      modifier = Modifier
        .width(drawerWidthDp)
        .fillMaxHeight()
        .absoluteOffset(offsetAnimated)
    ) {
      RouterHost(
        navigationRouter = childRouter,
        defaultScreen = { drawerScreen.Content() }
      )
    }
  }
}