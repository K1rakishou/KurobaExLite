package com.github.k1rakishou.kurobaexlite.ui.elements.snackbar

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.album.AlbumScreen
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.helpers.util.ensureSingleMeasurable
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


@Composable
fun KurobaSnackbarContainer(
  modifier: Modifier = Modifier,
  screenKey: ScreenKey,
  isTablet: Boolean,
  kurobaSnackbarState: KurobaSnackbarState,
  animationDuration: Int = 200
) {
  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.BottomCenter
  ) {
    val snackbarManager = koinRemember<SnackbarManager>()

    val insets = LocalWindowInsets.current
    val chanTheme = LocalChanTheme.current
    val currentOrientation = LocalConfiguration.current.orientation
    val currentOrientationUpdated by rememberUpdatedState(newValue = currentOrientation)

    val maxContainerWidth = remember(key1 = maxWidth) { maxWidth - 16.dp }
    val maxSnackbarWidth = (if (isTablet) 600.dp else 400.dp).coerceAtMost(maxContainerWidth)

    LaunchedEffect(
      key1 = kurobaSnackbarState,
      block = {
        snackbarManager.snackbarEventFlow.collect { snackbarInfoEvent ->
          when (snackbarInfoEvent) {
            is SnackbarInfoEvent.Push -> {
              val evenScreenKey = safeScreenKey(snackbarInfoEvent.snackbarInfo.screenKey)
              if (evenScreenKey == screenKey) {
                kurobaSnackbarState.pushSnackbar(snackbarInfoEvent.snackbarInfo)
              }
            }
            is SnackbarInfoEvent.Pop -> {
              kurobaSnackbarState.popSnackbar(snackbarInfoEvent.id)
            }
          }
        }
      }
    )

    val activeSnackbars = kurobaSnackbarState.activeSnackbars
    val snackbarAnimations = kurobaSnackbarState.snackbarAnimations
    val visibleSnackbarSizeMap = remember { mutableStateMapOf<SnackbarId, IntSize>() }

    LaunchedEffect(
      key1 = Unit,
      block = {
        while (true) {
          delay(500)
          kurobaSnackbarState.removeOldSnackbars()

          if (visibleSnackbarSizeMap.isEmpty()) {
            continue
          }

          val totalTakenHeight = visibleSnackbarSizeMap.values
            .sumOf { intSize -> intSize.height }

          val multiplier = when (currentOrientationUpdated) {
            Configuration.ORIENTATION_PORTRAIT -> 0.5f
            Configuration.ORIENTATION_LANDSCAPE -> 0.7f
            else -> 0.5f
          }

          val maxAvailableHeight = (constraints.maxHeight * multiplier).toInt()

          if (totalTakenHeight > maxAvailableHeight) {
            kurobaSnackbarState.removeSnackbarsExceedingAvailableHeight(
              visibleSnackbarSizeMap = visibleSnackbarSizeMap.toMap(),
              maxAvailableHeight = maxAvailableHeight
            )
          }
        }
      }
    )

    LayoutSnackbarItems(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(
          top = insets.top,
          bottom = insets.bottom
        )
        .requiredWidthIn(max = maxSnackbarWidth),
      snackbars = activeSnackbars,
      animationSpecProvider = { tween(durationMillis = animationDuration) }
    ) { snackbarInfo ->
      key(snackbarInfo.snackbarId) {
        val snackbarAnimation = snackbarAnimations[snackbarInfo.snackbarIdForCompose]

        BoxWithConstraints {
          KurobaSnackbarItem(
            containerWidth = constraints.maxWidth,
            layoutAnimationIsInProgress = animationInProgress,
            animationDuration = animationDuration,
            isTablet = isTablet,
            chanTheme = chanTheme,
            snackbarInfo = snackbarInfo,
            snackbarAnimation = snackbarAnimation,
            onSnackbarSizeChanged = { snackbarId, intSize -> visibleSnackbarSizeMap[snackbarId] = intSize },
            onSnackbarDisposed = { snackbarId -> visibleSnackbarSizeMap.remove(snackbarId) },
            onAnimationEnded = { finishedAnimation -> kurobaSnackbarState.onAnimationEnded(finishedAnimation) },
            dismissSnackbar = { snackbarId -> snackbarManager.popSnackbar(snackbarId) }
          )
        }
      }
    }
  }
}

@Composable
private fun LayoutSnackbarItems(
  modifier: Modifier = Modifier,
  snackbars: List<SnackbarInfo>,
  animationSpecProvider: () -> AnimationSpec<Int>,
  snackbarContent: @Composable SnackbarAnimationScope.(SnackbarInfo) -> Unit
) {
  val subcomposeLayoutState = remember { SubcomposeLayoutState() }
  val animationDataMap = remember { mutableStateMapOf<SnackbarId, AnimationData>() }
  val coroutineScope = rememberCoroutineScope()

  SubcomposeLayout(
    modifier = modifier,
    state = subcomposeLayoutState,
    measurePolicy = { constraints ->
      if (snackbars.isEmpty()) {
        return@SubcomposeLayout layout(0, 0) {  }
      }

      require(constraints.hasBoundedHeight) { "Height must not be infinite" }

      val placeables = arrayOfNulls<Placeable>(snackbars.size)
      val animatables = arrayOfNulls<Animatable<Int, AnimationVector1D>>(snackbars.size)

      var maxHeight = constraints.maxHeight

      for ((index, snackbarInfo) in snackbars.withIndex().reversed()) {
        val animationInProgress = animationDataMap[snackbarInfo.snackbarId]?.animatable?.isRunning ?: false
        val snackbarAnimationScope = SnackbarAnimationScope(animationInProgress)

        val measurable = subcompose(
          slotId = snackbarInfo.snackbarId,
          content = { with(snackbarAnimationScope) { snackbarContent(snackbarInfo) } }
        ).ensureSingleMeasurable()

        val placeable = measurable.measure(constraints.copy(minHeight = 0))

        val startY = maxHeight
        maxHeight -= placeable.measuredHeight
        val targetY = maxHeight

        var animationData = animationDataMap[snackbarInfo.snackbarId]
        if (animationData != null && targetY != animationData.animatable.targetValue) {
          animationData.startAnimation(index, targetY)
        } else if (animationData == null) {
          animationData = AnimationData(
            coroutineScope = coroutineScope,
            animationSpec = animationSpecProvider(),
            animatable = Animatable(
              initialValue = startY,
              typeConverter = Int.VectorConverter,
              visibilityThreshold = 1
            )
          )

          animationDataMap[snackbarInfo.snackbarId] = animationData
          animationData.startAnimation(index, targetY)
        }

        animatables[index] = animationData.animatable
        placeables[index] = placeable
      }

      return@SubcomposeLayout layout(
        width = constraints.maxWidth,
        height = constraints.maxHeight
      ) {
        for ((index, placeable) in placeables.withIndex()) {
          val animatable = animatables[index]!!
          placeable!!.place(x = 0, y = animatable.value)
        }
      }
    }
  )
}

class SnackbarAnimationScope(
  val animationInProgress: Boolean
)

private class AnimationData(
  private val coroutineScope: CoroutineScope,
  private val animationSpec: AnimationSpec<Int>,
  val animatable: Animatable<Int, AnimationVector1D>
) {
  private var prevJob: Job? = null

  fun startAnimation(
    index: Int,
    targetY: Int
  ) {
    prevJob?.cancel()
    prevJob = coroutineScope.launch {
      delay(index * 25L)

      animatable.animateTo(
        targetValue = targetY,
        animationSpec = animationSpec
      )
    }
  }

}

/**
 * Snackbars can be only shown on MainScreen, CatalogScreen, ThreadScreen and AlbumScreen.
 * If the [screenKey] is neither of them then use MainScreen.SCREEN_KEY
 * Otherwise the snackbar won't be shown at all!
 * */
private fun safeScreenKey(screenKey: ScreenKey): ScreenKey {
  if (screenKey == CatalogScreen.SCREEN_KEY) {
    return screenKey
  }

  if (screenKey == ThreadScreen.SCREEN_KEY) {
    return screenKey
  }

  if (screenKey == AlbumScreen.SCREEN_KEY) {
    return screenKey
  }

  return MainScreen.SCREEN_KEY
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun KurobaSnackbarItem(
  containerWidth: Int,
  layoutAnimationIsInProgress: Boolean,
  animationDuration: Int,
  isTablet: Boolean,
  chanTheme: ChanTheme,
  snackbarInfo: SnackbarInfo,
  snackbarAnimation: SnackbarAnimation?,
  onSnackbarSizeChanged: (SnackbarId, IntSize) -> Unit,
  onSnackbarDisposed: (SnackbarId) -> Unit,
  onAnimationEnded: (SnackbarAnimation) -> Unit,
  dismissSnackbar: (SnackbarId) -> Unit
) {
  val snackbarManager: SnackbarManager = koinRemember()
  val snackbarType = snackbarInfo.snackbarType

  val containerHorizPadding = if (isTablet) 14.dp else 10.dp
  val containerVertPadding = if (isTablet) 10.dp else 6.dp
  var contentHorizPadding = if (isTablet) 10.dp else 6.dp
  var contentVertPadding = if (isTablet) 14.dp else 8.dp
  val maxSnackbarAlpha = if (snackbarInfo.snackbarType.isToast) 0.8f else 1f

  if (snackbarType.isToast) {
    contentHorizPadding *= 1.5f
    contentVertPadding *= 1.25f
  }

  val backgroundColor = when (snackbarInfo.snackbarType) {
    SnackbarType.Default -> chanTheme.backColorSecondary
    SnackbarType.Toast -> Color.White
    SnackbarType.ErrorToast -> chanTheme.errorColor
  }

  var animatedAlpha by remember { mutableStateOf(0f) }
  var fadeInOrOutAnimationJob by remember { mutableStateOf<Job?>(null) }

  val snackbarAnimationUpdated by rememberUpdatedState(newValue = snackbarAnimation)

  DisposableEffect(
    key1 = Unit,
    effect = { onDispose { onSnackbarDisposed(snackbarInfo.snackbarId) } }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      snapshotFlow { snackbarAnimationUpdated }
        .collect { latestSnackbarAnimation ->
          if (latestSnackbarAnimation == null) {
            return@collect
          }

          when (latestSnackbarAnimation) {
            is SnackbarAnimation.Push,
            is SnackbarAnimation.Pop -> {
              fadeInOrOutAnimationJob?.cancel()
              fadeInOrOutAnimationJob = launch {
                animateFadeInOrOut(
                  animationDuration = animationDuration,
                  maxSnackbarAlpha = maxSnackbarAlpha,
                  snackbarAnimation = latestSnackbarAnimation,
                  onAnimationTick = { alpha ->
                    animatedAlpha = alpha
                  },
                  onAnimationEnded = { snackbarAnimation ->
                    onAnimationEnded(snackbarAnimation)
                    fadeInOrOutAnimationJob = null
                  }
                )
              }
            }
          }
        }
    }
  )

  val animationInProgress = layoutAnimationIsInProgress || fadeInOrOutAnimationJob != null
  val hasClickableItems = remember(key1 = snackbarInfo) { snackbarInfo.hasClickableItems }
  val canBeSwipedAway = hasClickableItems && snackbarInfo.aliveUntil != null

  val clickableModifier = if (animationInProgress) {
    Modifier
  } else {
    Modifier.kurobaClickable(
      onClick = {
        if (hasClickableItems) {
          return@kurobaClickable
        }

        dismissSnackbar(snackbarInfo.snackbarId)
      }
    )
  }

  val anchors = remember(key1 = containerWidth) {
    mapOf(
      (-containerWidth.toFloat()) to Anchors.SwipedLeft,
      0f to Anchors.Visible,
      containerWidth.toFloat() to Anchors.SwipedRight
    )
  }

  val swipeableState = rememberSwipeableState(initialValue = Anchors.Visible)
  val currentOffset by swipeableState.offset
  val currentValue = swipeableState.currentValue

  LaunchedEffect(
    key1 = currentValue,
    block = {
      if (currentValue != Anchors.Visible) {
        dismissSnackbar(snackbarInfo.snackbarId)
      }
    }
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .swipeable(
        enabled = canBeSwipedAway,
        state = swipeableState,
        anchors = anchors,
        orientation = Orientation.Horizontal,
        thresholds = { _, _ -> FractionalThreshold(.5f) },
      )
      .absoluteOffset { IntOffset(currentOffset.toInt(), 0) }
      .graphicsLayer { alpha = animatedAlpha }
      .onGloballyPositioned { layoutCoordinates ->
        onSnackbarSizeChanged(snackbarInfo.snackbarId, layoutCoordinates.size)
      },
    contentAlignment = Alignment.Center
  ) {
    KurobaComposeCard(
      modifier = Modifier
        .padding(
          horizontal = containerHorizPadding,
          vertical = containerVertPadding
        )
        .wrapContentWidth()
        .then(clickableModifier),
      backgroundColor = backgroundColor,
      elevation = 4.dp
    ) {
      Row(
        modifier = Modifier
          .wrapContentHeight()
          .padding(
            horizontal = contentHorizPadding,
            vertical = contentVertPadding
          ),
        verticalAlignment = Alignment.CenterVertically
      ) {
        KurobaSnackbarContent(
          isTablet = isTablet,
          snackbarType = snackbarType,
          snackbarInfo = snackbarInfo,
          backgroundColor = backgroundColor,
          onSnackbarClicked = { snackbarClickable, snackbarId ->
            snackbarManager.onSnackbarElementClicked(snackbarClickable)
            dismissSnackbar(snackbarId)
          },
          onDismissSnackbar = { snackbarId -> dismissSnackbar(snackbarId) }
        )
      }
    }
  }
}

private suspend fun animateFadeInOrOut(
  animationDuration: Int,
  maxSnackbarAlpha: Float,
  snackbarAnimation: SnackbarAnimation,
  onAnimationTick: (alpha: Float) -> Unit,
  onAnimationEnded: (SnackbarAnimation) -> Unit
) {
  try {
    when (snackbarAnimation) {
      is SnackbarAnimation.Push -> {
        try {
          animate(
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = animationDuration,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { progress, _ ->
            val animatedAlpha = (progress * maxSnackbarAlpha)
            onAnimationTick(animatedAlpha)
          }
        } finally {
          val animatedAlpha = (1f * maxSnackbarAlpha)
          onAnimationTick(animatedAlpha)
        }
      }
      is SnackbarAnimation.Pop -> {
        try {
          animate(
            initialValue = 1f,
            targetValue = 0f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = animationDuration,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { progress, _ ->
            val animatedAlpha = (progress * maxSnackbarAlpha)
            onAnimationTick(animatedAlpha)
          }
        } finally {
          val animatedAlpha = (0f * maxSnackbarAlpha)
          onAnimationTick(animatedAlpha)
        }
      }
    }
  } finally {
    onAnimationEnded(snackbarAnimation)
  }

}

@Composable
private fun RowScope.KurobaSnackbarContent(
  isTablet: Boolean,
  snackbarType: SnackbarType,
  snackbarInfo: SnackbarInfo,
  backgroundColor: Color,
  onSnackbarClicked: (SnackbarClickable, SnackbarId) -> Unit,
  onDismissSnackbar: (SnackbarId) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val textSize = if (isTablet) 18.sp else 16.sp

  val hasClickableItems = snackbarInfo.hasClickableItems
  val aliveUntil = snackbarInfo.aliveUntil
  val snackbarId = snackbarInfo.snackbarId
  val snackbarIdForCompose = snackbarInfo.snackbarIdForCompose
  val content = snackbarInfo.content

  if (!snackbarType.isToast && hasClickableItems && aliveUntil != null) {
    val startTime = remember(key1 = snackbarIdForCompose) { SystemClock.elapsedRealtime() }
    var progress by remember(key1 = snackbarIdForCompose) { mutableStateOf(1f) }

    LaunchedEffect(
      key1 = snackbarIdForCompose,
      block = {
        val timeDelta = aliveUntil - startTime
        val delayMs = 16 * 5L

        while (isActive) {
          val currentTimeDelta = aliveUntil - SystemClock.elapsedRealtime()
          if (currentTimeDelta < 0) {
            break
          }

          progress = currentTimeDelta.toFloat() / timeDelta.toFloat()
          delay(delayMs)
        }

        progress = 0f
      }
    )

    Box(
      modifier = Modifier
        .size(30.dp)
        .kurobaClickable(
          bounded = false,
          onClick = { onDismissSnackbar(snackbarId) }
        ),
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeLoadingIndicator(
        progress = progress,
        modifier = Modifier.wrapContentSize(),
        indicatorSize = 24.dp
      )

      KurobaComposeIcon(
        modifier = Modifier.size(14.dp),
        drawableId = R.drawable.ic_baseline_close_24
      )
    }

    Spacer(modifier = Modifier.width(8.dp))
  }

  for (snackbarContentItem in content) {
    when (snackbarContentItem) {
      SnackbarContentItem.LoadingIndicator -> {
        KurobaComposeLoadingIndicator(
          modifier = Modifier.wrapContentSize(),
          indicatorSize = 24.dp,
          fadeInTimeMs = 0
        )
      }
      is SnackbarContentItem.Spacer -> {
        Spacer(modifier = Modifier.width(snackbarContentItem.space))
      }
      is SnackbarContentItem.Text -> {
        val widthModifier = if (snackbarType.isToast || !snackbarContentItem.takeWholeWidth) {
          Modifier.wrapContentWidth()
        } else {
          Modifier.weight(1f)
        }

        val textColor = if (snackbarContentItem.textColor != null) {
          snackbarContentItem.textColor
        } else {
          when (snackbarType) {
            SnackbarType.Default -> null
            SnackbarType.ErrorToast -> Color.White
            SnackbarType.Toast -> Color.Black
          }
        }

        KurobaComposeText(
          modifier = widthModifier,
          fontSize = textSize,
          color = textColor,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          text = snackbarContentItem.formattedText
        )
      }
      is SnackbarContentItem.Button -> {
        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          text = snackbarContentItem.formattedText,
          customTextColor = snackbarContentItem.textColor ?: chanTheme.accentColor,
          onClick = { onSnackbarClicked(snackbarContentItem, snackbarId) }
        )
      }
      is SnackbarContentItem.Icon -> {
        val iconColor = remember(key1 = backgroundColor) {
          if (ThemeEngine.isDarkColor(backgroundColor)) {
            Color.White
          } else {
            Color.Black
          }
        }

        KurobaComposeIcon(
          modifier = Modifier
            .wrapContentSize()
            .padding(4.dp)
            .align(Alignment.CenterVertically),
          drawableId = snackbarContentItem.drawableId,
          iconColor = iconColor
        )
      }
    }
  }
}

private enum class Anchors {
  SwipedLeft,
  Visible,
  SwipedRight
}