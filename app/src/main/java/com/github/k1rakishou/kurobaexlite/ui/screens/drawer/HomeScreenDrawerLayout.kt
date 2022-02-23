package com.github.k1rakishou.kurobaexlite.ui.screens.drawer

import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import kotlinx.coroutines.delay


private val COLOR_INVISIBLE = Color(0x00000000)
private val COLOR_VISIBLE = Color(0x80000000)

@Composable
fun HomeScreenDrawerLayout(
  drawerWidth: Int,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  homeScreenViewModel: HomeScreenViewModel
) {
  val childRouter = remember { navigationRouter.childRouter(DrawerScreen.SCREEN_KEY.key) }
  val drawerScreen = remember { DrawerScreen(componentActivity, navigationRouter) }
  val drawerVisibility by homeScreenViewModel.drawerVisibilityFlow.collectAsState()
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
        targetState is HomeScreenViewModel.DrawerVisibility.Closing
        || targetState is HomeScreenViewModel.DrawerVisibility.Opening
      ) {
        tween(durationMillis = 250)
      } else {
        snap()
      }
    },
    targetValueByState = { dv ->
      return@animateDp when (dv) {
        HomeScreenViewModel.DrawerVisibility.Closing -> maxOffsetDp
        HomeScreenViewModel.DrawerVisibility.Opening -> 0.dp
        is HomeScreenViewModel.DrawerVisibility.Drag -> maxOffsetDp * dv.progressInverted
        HomeScreenViewModel.DrawerVisibility.Closed -> maxOffsetDp
        HomeScreenViewModel.DrawerVisibility.Opened -> 0.dp
      }
    }
  )

  val backgroundAnimated by transition.animateColor(
    label = "Color animation",
    transitionSpec = {
      if (
        targetState is HomeScreenViewModel.DrawerVisibility.Closing
        || targetState is HomeScreenViewModel.DrawerVisibility.Opening
      ) {
        tween(durationMillis = 250)
      } else {
        snap()
      }
    },
    targetValueByState = { dv ->
      return@animateColor when (dv) {
        HomeScreenViewModel.DrawerVisibility.Closing -> COLOR_INVISIBLE
        HomeScreenViewModel.DrawerVisibility.Opening -> COLOR_VISIBLE
        is HomeScreenViewModel.DrawerVisibility.Drag -> {
          COLOR_VISIBLE.copy(alpha = COLOR_VISIBLE.alpha * dv.progress)
        }
        HomeScreenViewModel.DrawerVisibility.Closed -> COLOR_INVISIBLE
        HomeScreenViewModel.DrawerVisibility.Opened -> COLOR_VISIBLE
      }
    }
  )

  val clickable = remember(key1 = drawerVisibility) {
    when (drawerVisibility) {
      HomeScreenViewModel.DrawerVisibility.Closed -> false
      HomeScreenViewModel.DrawerVisibility.Closing,
      is HomeScreenViewModel.DrawerVisibility.Drag,
      HomeScreenViewModel.DrawerVisibility.Opened,
      HomeScreenViewModel.DrawerVisibility.Opening -> true
    }
  }

  val clickableModifier = remember(key1 = clickable) {
    if (clickable) {
      Modifier.kurobaClickable(hasClickIndication = false) { homeScreenViewModel.closeDrawer() }
    } else {
      Modifier
    }
  }

  val isAnimationRunning = if (drawerVisibility is HomeScreenViewModel.DrawerVisibility.Drag) {
    (drawerVisibility as HomeScreenViewModel.DrawerVisibility.Drag).isDragging
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

        val dv = drawerVisibility
        when (dv) {
          HomeScreenViewModel.DrawerVisibility.Closing -> {
            homeScreenViewModel.closeDrawer(withAnimation = false)
          }
          HomeScreenViewModel.DrawerVisibility.Opening -> {
            homeScreenViewModel.openDrawer(withAnimation = false)
          }
          is HomeScreenViewModel.DrawerVisibility.Drag -> {
            when {
              dv.velocity > velocityEnoughToAnimate -> homeScreenViewModel.openDrawer()
              dv.velocity < -velocityEnoughToAnimate -> homeScreenViewModel.closeDrawer()
              else -> {
                if (dv.progress > 0.5f) {
                  homeScreenViewModel.openDrawer()
                } else {
                  homeScreenViewModel.closeDrawer()
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