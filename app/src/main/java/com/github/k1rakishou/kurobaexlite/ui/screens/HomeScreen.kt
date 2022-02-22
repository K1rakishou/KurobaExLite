package com.github.k1rakishou.kurobaexlite.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.helpers.kurobaAwaitLongPressOrCancellation
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.elements.ExperimentalPagerApi
import com.github.k1rakishou.kurobaexlite.ui.elements.HorizontalPager
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.PagerState
import com.github.k1rakishou.kurobaexlite.ui.elements.pager.rememberPagerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.screens.drawer.DrawerScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeChildScreens
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.HomeScreenToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val homeScreenViewModel: HomeScreenViewModel by componentActivity.viewModel()
  private val homeChildScreens by lazy { HomeChildScreens(componentActivity, navigationRouter) }

  override val screenKey: ScreenKey = SCREEN_KEY

  @OptIn(ExperimentalPagerApi::class)
  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val insets = LocalWindowInsets.current
    val configuration = LocalConfiguration.current
    val childScreens = homeChildScreens.getChildScreens(configuration)
    val initialScreenIndex = homeChildScreens.getInitialScreenIndex(configuration, childScreens)

    val drawerLongtapGestureZonePx = with(LocalDensity.current) { remember { 24.dp.toPx() } }
    val maxDrawerWidth = with(LocalDensity.current) { remember { 600.dp.toPx().toInt() } }
    val drawerPhoneVisibleWindowWidth = with(LocalDensity.current) { remember { 40.dp.toPx().toInt() } }
    var drawerWidth by remember { mutableStateOf(0) }

    // rememberSaveable currently does not recreate objects when it's keys change, instead it uses
    // the restored object from the saver. This causes crashes when going from portrait to landscape
    // when we are currently on a thread viewpager page since the pagerState.currentPage ends up
    // being 2 while there are only 2 screens in landscape mode.
    // There is an issue to add support for that on the google's issues tracker but it's almost
    // 2 years old. So for the time being we have to hack around the issue.
    val pagerState = rememberPagerState(key1 = configuration.orientation, initialPage = initialScreenIndex)

    LaunchedEffect(
      key1 = Unit,
      block = {
        homeScreenViewModel.currentPageFlow.collect { currentPage ->
          scrollToPageByScreenKey(
            screenKey = currentPage.screenKey,
            childScreens = childScreens,
            pagerState = pagerState,
            animate = currentPage.animate
          )
        }
      })

    homeChildScreens.HandleBackPresses()

    Box(
      modifier = Modifier
        .onSizeChanged { size ->
          drawerWidth = calculateDrawerWidth(size, maxDrawerWidth, drawerPhoneVisibleWindowWidth)
        }
        .pointerInput(
          key1 = Unit,
          block = {
            detectDrawerDragGestures(
              drawerLongtapGestureZonePx = drawerLongtapGestureZonePx,
              drawerPhoneVisibleWindowWidthPx = drawerPhoneVisibleWindowWidth.toFloat(),
              drawerWidth = drawerWidth.toFloat(),
              onDraggingDrawer = { dragging, progress, velocity ->
                homeScreenViewModel.dragDrawer(dragging, progress, velocity)
              })
          }
        )
    ) {
      HorizontalPager(
        modifier = Modifier.fillMaxSize(),
        state = pagerState,
        count = childScreens.size
      ) { page ->
        LaunchedEffect(
          key1 = pagerState.currentPage,
          block = {
            val screenKey = childScreens.getOrNull(pagerState.currentPage)?.screenKey
              ?: return@LaunchedEffect

            // When we manually scroll the pager we need to keep track of the current page,
            // however we don't want to notify the listeners in this case.
            homeScreenViewModel.updateCurrentPage(
              screenKey = screenKey,
              animate = false,
              notifyListeners = false
            )
          }
        )

        val childScreen = childScreens[page]
        val transitionIsProgress = pagerState.currentPage != pagerState.targetPage
        val currentPageOffset = Math.abs(pagerState.currentPageOffset)

        val scale = if (transitionIsProgress) {
          if (currentPageOffset <= 0.5f) {
            lerpFloat(transitionScaleMax, transitionScaleMin, currentPageOffset)
          } else {
            lerpFloat(transitionScaleMin, transitionScaleMax, currentPageOffset)
          }
        } else {
          transitionScaleMax
        }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .scale(scale)
            .consumeClicks(consume = transitionIsProgress)
        ) {
          childScreen.Content()
        }
      }

      HomeScreenToolbarContainer(
        insets = insets,
        chanTheme = chanTheme,
        pagerState = pagerState,
        childScreens = childScreens,
        homeScreenViewModel = homeScreenViewModel
      )

      DrawerLayout(drawerWidth)
    }
  }

  private fun calculateDrawerWidth(
    size: IntSize,
    maxDrawerWidth: Int,
    drawerPhoneVisibleWindowWidth: Int
  ): Int {
    if (uiInfoManager.isTablet) {
      return size.width.coerceAtMost(maxDrawerWidth)
    }

    return size.width - drawerPhoneVisibleWindowWidth
  }

  private suspend fun PointerInputScope.detectDrawerDragGestures(
    drawerLongtapGestureZonePx: Float,
    drawerPhoneVisibleWindowWidthPx: Float,
    drawerWidth: Float,
    onDraggingDrawer: (Boolean, Float, Float) -> Unit
  ) {
    val velocityTracker = VelocityTracker()

    forEachGesture {
      val firstEvent = awaitPointerEventScope { awaitPointerEvent(pass = PointerEventPass.Initial) }
      if (firstEvent.type != PointerEventType.Press) {
        return@forEachGesture
      }

      if (drawerWidth <= 0) {
        return@forEachGesture
      }

      val downEvent = firstEvent.changes.firstOrNull()
        ?: return@forEachGesture

      var prevDragProgress = 0f

      if (homeScreenViewModel.isDrawerOpened()) {
        var overSlop = Offset.Zero

        val touchSlopChange = awaitPointerEventScope {
          awaitTouchSlopOrCancellation(downEvent.id) { change, slop ->
            change.consumeAllChanges()
            overSlop = slop
          }
        }

        if (touchSlopChange == null || Math.abs(overSlop.y) > Math.abs(overSlop.x)) {
          return@forEachGesture
        }

        if (downEvent.position.x > (drawerWidth - drawerPhoneVisibleWindowWidthPx)) {
          return@forEachGesture
        }
      } else {
        val longPress = kurobaAwaitLongPressOrCancellation(downEvent)
        if (longPress == null || longPress.position.x > (drawerLongtapGestureZonePx)) {
          return@forEachGesture
        }

        val dragProgress = (longPress.position.x / drawerWidth).coerceIn(0f, 1f)
        prevDragProgress = dragProgress

        onDraggingDrawer(true, dragProgress, 0f)
      }

      try {
        awaitPointerEventScope {
          for (change in firstEvent.changes) {
            velocityTracker.addPointerInputChange(change)
            change.consumeAllChanges()
          }

          while (true) {
            val moveEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
            val drag = moveEvent.changes.firstOrNull { it.id == downEvent.id }
              ?: break

            if (drag.changedToUpIgnoreConsumed()) {
              break
            }

            velocityTracker.addPointerInputChange(drag)

            val dragProgress = (drag.position.x / drawerWidth).coerceIn(0f, 1f)
            prevDragProgress = dragProgress

            onDraggingDrawer(true, dragProgress, 0f)

            for (change in moveEvent.changes) {
              change.consumeAllChanges()
            }
          }
        }
      } finally {
        val velocityX = velocityTracker.calculateVelocity().x

        onDraggingDrawer(false, prevDragProgress, velocityX)
        velocityTracker.resetTracking()
      }
    }
  }

  @Composable
  private fun DrawerLayout(drawerWidth: Int) {
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

  @OptIn(ExperimentalPagerApi::class)
  private suspend fun scrollToPageByScreenKey(
    screenKey: ScreenKey,
    childScreens: List<ComposeScreen>,
    pagerState: PagerState,
    animate: Boolean
  ) {
    val indexOfPage = childScreens
      .indexOfFirst { it.screenKey == screenKey }

    if (indexOfPage >= 0) {
      if (animate) {
        pagerState.animateScrollToPage(page = indexOfPage)
      } else {
        pagerState.scrollToPage(page = indexOfPage)
      }
    }
  }

  companion object {
    val SCREEN_KEY = ScreenKey("HomeScreen")

    private const val transitionScaleMax = 1f
    private const val transitionScaleMin = 0.95f

    private val COLOR_INVISIBLE = Color(0x00000000)
    private val COLOR_VISIBLE = Color(0x80000000)
  }
}