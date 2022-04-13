package com.github.k1rakishou.kurobaexlite.ui.helpers

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.ViewModel
import com.github.k1rakishou.kurobaexlite.features.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.helpers.lerpFloat
import com.github.k1rakishou.kurobaexlite.helpers.rememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.removeIfKt

@Composable
fun <T> AnimateableStackContainer(
  state: AnimateableStackContainerState<T>,
  content: @Composable (T) -> Unit
) {
  val composedItems = remember { mutableMapOf<T, @Composable (T) -> Unit>() }

  // First iterate and draw all fixed in place elements which are not currently being animated
  for (addedElementWrapper in state.addedElementWrappers) {
    val element = addedElementWrapper.element

    key(addedElementWrapper.key) {
      val movableContent = composedItems.getOrPut(
        key = element,
        defaultValue = { movableContentOf<T> { content(element) } }
      )

      movableContent(element)
    }
  }

  // Then iterate and draw elements which are currently being animated
  for (animatingElement in state.animatingElements) {
    val element = animatingElement.elementWrapper.element

    key(animatingElement.elementWrapper.key) {
      val movableContent = composedItems.getOrPut(
        key = element,
        defaultValue = { movableContentOf<T> { content(element) } }
      )

      StackContainerTransition(
        stackContainerAnimation = animatingElement,
        onAnimationFinished = { state.onAnimationFinished() }
      ) {
        movableContent(element)
      }
    }
  }
}

@Composable
fun <T> rememberAnimateableStackContainerState(
  screenKey: ScreenKey,
  initialValues: List<StackContainerElementWrapper<T>> = emptyList()
): AnimateableStackContainerState<T> {
  val componentActivity = LocalComponentActivity.current
  val viewModel = componentActivity.rememberViewModel<AnimateableStackContainerViewModel>()

  return viewModel.storage.getOrPut(
    key = screenKey,
    defaultValue = { AnimateableStackContainerState(initialValues) }
  ) as AnimateableStackContainerState<T>
}

class AnimateableStackContainerViewModel : ViewModel() {
  internal val storage = mutableMapOf<ScreenKey, AnimateableStackContainerState<*>>()
}

@Composable
private fun <T> StackContainerTransition(
  animationDuration: Int = 250,
  stackContainerAnimation: StackContainerAnimation<T>,
  onAnimationFinished: () -> Unit,
  content: @Composable () -> Unit
) {
  val scaleInitial = when (stackContainerAnimation) {
    is StackContainerAnimation.Remove -> 0f
    is StackContainerAnimation.Set -> 1f
    is StackContainerAnimation.Push -> .85f
    is StackContainerAnimation.Pop -> 1f
    is StackContainerAnimation.Fade -> {
      when (stackContainerAnimation.fadeType) {
        is StackContainerAnimation.FadeType.In -> 0f
        is StackContainerAnimation.FadeType.Out -> 1f
      }
    }
  }

  val alphaInitial = when (stackContainerAnimation) {
    is StackContainerAnimation.Remove -> 0f
    is StackContainerAnimation.Set -> 1f
    is StackContainerAnimation.Push -> 0f
    is StackContainerAnimation.Pop -> 1f
    is StackContainerAnimation.Fade -> {
      when (stackContainerAnimation.fadeType) {
        is StackContainerAnimation.FadeType.In -> 0f
        is StackContainerAnimation.FadeType.Out -> 1f
      }
    }
  }

  val canRenderInitial = when (stackContainerAnimation) {
    is StackContainerAnimation.Remove -> true
    is StackContainerAnimation.Set -> false
    is StackContainerAnimation.Push -> false
    is StackContainerAnimation.Pop -> true
    is StackContainerAnimation.Fade -> {
      when (stackContainerAnimation.fadeType) {
        is StackContainerAnimation.FadeType.In -> false
        is StackContainerAnimation.FadeType.Out -> true
      }
    }
  }

  var scaleAnimated by remember { mutableStateOf(scaleInitial) }
  var alphaAnimated by remember { mutableStateOf(alphaInitial) }
  var canRender by remember { mutableStateOf(canRenderInitial) }

  LaunchedEffect(
    key1 = stackContainerAnimation,
    block = {
      try {
        animateInternal(
          stackContainerAnimation = stackContainerAnimation,
          animationDuration = animationDuration,
          onCanRenderChanged = { newValue -> canRender = newValue },
          onScaleChanged = { newValue -> scaleAnimated = newValue },
          onAlphaChanged = { newValue -> alphaAnimated = newValue },
        )
      } finally {
        onAnimationFinished()
      }
    }
  )

  if (canRender) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(
          alpha = alphaAnimated,
          scaleX = scaleAnimated,
          scaleY = scaleAnimated
        )
    ) {
      content()
    }
  }
}

private suspend fun <T> animateInternal(
  stackContainerAnimation: StackContainerAnimation<T>,
  animationDuration: Int,
  onCanRenderChanged: (Boolean) -> Unit,
  onScaleChanged: (Float) -> Unit,
  onAlphaChanged: (Float) -> Unit
) {
  when (stackContainerAnimation) {
    is StackContainerAnimation.Remove -> {
      onCanRenderChanged(false)
      onScaleChanged(0f)
      onAlphaChanged(0f)
    }
    is StackContainerAnimation.Set -> {
      onCanRenderChanged(true)
      onScaleChanged(1f)
      onAlphaChanged(1f)
    }
    is StackContainerAnimation.Push -> {
      val scaleStart = .85f
      val scaleEnd = 1f
      onCanRenderChanged(true)

      animate(
        initialValue = 0f,
        targetValue = 1f,
        initialVelocity = 0f,
        animationSpec = FloatTweenSpec(
          duration = animationDuration,
          delay = 0,
          easing = FastOutSlowInEasing
        )
      ) { animationProgress, _ ->
        onScaleChanged(lerpFloat(scaleStart, scaleEnd, animationProgress))
        onAlphaChanged(animationProgress)
      }
    }
    is StackContainerAnimation.Pop -> {
      val scaleStart = 1f
      val scaleEnd = .85f

      animate(
        initialValue = 1f,
        targetValue = 0f,
        initialVelocity = 0f,
        animationSpec = FloatTweenSpec(
          duration = animationDuration,
          delay = 0,
          easing = FastOutSlowInEasing
        )
      ) { animationProgress, _ ->
        onScaleChanged(lerpFloat(scaleStart, scaleEnd, animationProgress))
        onAlphaChanged(animationProgress)
      }

      onCanRenderChanged(true)
    }
    is StackContainerAnimation.Fade -> {
      onScaleChanged(1f)

      val initialValue = when (stackContainerAnimation.fadeType) {
        is StackContainerAnimation.FadeType.In -> 0f
        is StackContainerAnimation.FadeType.Out -> 1f
      }

      val targetValue = when (stackContainerAnimation.fadeType) {
        is StackContainerAnimation.FadeType.In -> 1f
        is StackContainerAnimation.FadeType.Out -> 0f
      }

      onCanRenderChanged(true)

      animate(
        initialValue = initialValue,
        targetValue = targetValue,
        initialVelocity = 0f,
        animationSpec = FloatTweenSpec(
          duration = animationDuration,
          delay = 0,
          easing = FastOutSlowInEasing
        )
      ) { animationProgress, _ ->
        onAlphaChanged(animationProgress)
      }

      val canRender = when (stackContainerAnimation.fadeType) {
        is StackContainerAnimation.FadeType.In -> true
        is StackContainerAnimation.FadeType.Out -> false
      }

      onCanRenderChanged(canRender)
    }
  }
}

abstract class StackContainerElementWrapper<T>(
  val element: T
) {
  abstract val key: Any
}

class SimpleStackContainerElement<T>(
  element: T,
  private val keyExtractor: (T) -> Any
) : StackContainerElementWrapper<T>(element) {
  private val _key by lazy { keyExtractor(element) }

  override val key: Any = _key
}

@Stable
class AnimateableStackContainerState<T>(
  initialValues: List<StackContainerElementWrapper<T>>,
  private val duplicateChecker: MutableSet<Any> = mutableSetOf(),
  private val _addedElementWrappers: SnapshotStateList<StackContainerAnimation<T>> = mutableStateListOf(),
  private val _animatingElements: SnapshotStateList<StackContainerAnimation<T>> = mutableStateListOf()
) {
  val addedElementWrappers: List<StackContainerElementWrapper<T>>
    get() = _addedElementWrappers.map { it.elementWrapper }
  val animatingElements: List<StackContainerAnimation<T>>
    get() = _animatingElements
  val addedElementsCount: Int
    get() = _addedElementWrappers.size

  private val data = mutableMapOf<Any, Any?>()

  init {
    initialValues.forEach { initialValue ->
      _addedElementWrappers += StackContainerAnimation.Set(initialValue)
      duplicateChecker.add(initialValue.key)
    }
  }

  fun storeData(key: Any, value: Any?) {
    data[key] = value
  }

  fun removeData(key: Any) {
    data.remove(key)
  }

  fun <T : Any?> readData(key: Any): T {
    return data[key] as T
  }

  fun set(elementWrapper: StackContainerElementWrapper<T>) {
    if (!duplicateChecker.add(elementWrapper.key)) {
      return
    }

    _addedElementWrappers += StackContainerAnimation.Set(elementWrapper)
  }

  fun fadeIn(elementWrapper: StackContainerElementWrapper<T>) {
    if (!duplicateChecker.add(elementWrapper.key)) {
      return
    }

    _animatingElements.clear()

    val lastIndex = _addedElementWrappers.lastIndex
    if (lastIndex >= 0) {
      val topElementWrapper = _addedElementWrappers.get(lastIndex).elementWrapper
      val fadeOut = StackContainerAnimation.Fade<T>(
        elementWrapper = topElementWrapper,
        fadeType = StackContainerAnimation.FadeType.Out(isRemoving = false),
      )

      _addedElementWrappers.removeAt(lastIndex)
      _animatingElements.add(fadeOut)
    }

    val fadeIn = StackContainerAnimation.Fade<T>(
      elementWrapper = elementWrapper,
      fadeType = StackContainerAnimation.FadeType.In,
    )
    _animatingElements.add(fadeIn)
  }

  fun push(elementWrapper: StackContainerElementWrapper<T>) {
    if (!duplicateChecker.add(elementWrapper.key)) {
      return
    }

    _animatingElements.clear()
    _animatingElements.add(StackContainerAnimation.Push<T>(elementWrapper))
  }

  fun popTillRoot() {
    _animatingElements.clear()

    while (addedElementsCount > 1) {
      removeTop(withAnimation = false)
    }
  }

  fun removeTop(withAnimation: Boolean = true): Boolean {
    val topElement = _addedElementWrappers.lastOrNull()
      ?: return false

    if (!duplicateChecker.remove(topElement.elementWrapper.key)) {
      return false
    }

    if (!withAnimation) {
      _addedElementWrappers.removeIfKt { it.elementWrapper.key == topElement.elementWrapper.key }
      duplicateChecker.remove(topElement.elementWrapper.key)

      return true
    }

    when (topElement) {
      is StackContainerAnimation.Fade -> {
        when (topElement.fadeType) {
          StackContainerAnimation.FadeType.In -> {
            val penultimateIndex = _addedElementWrappers.lastIndex - 1
            if (penultimateIndex >= 0) {
              val penultimateElementWrapper = _addedElementWrappers.get(penultimateIndex).elementWrapper
              val fadeIn = StackContainerAnimation.Fade<T>(
                elementWrapper = penultimateElementWrapper,
                fadeType = StackContainerAnimation.FadeType.In
              )

              _addedElementWrappers.removeIfKt { it.elementWrapper.key == penultimateElementWrapper.key }
              _animatingElements.add(fadeIn)
            }

            val fadeOut = StackContainerAnimation.Fade(
              topElement.elementWrapper,
              StackContainerAnimation.FadeType.Out(isRemoving = true)
            )
            _addedElementWrappers.removeIfKt { it.elementWrapper.key == topElement.elementWrapper.key }
            _animatingElements.add(fadeOut)

            return true
          }
          is StackContainerAnimation.FadeType.Out -> {
            // already removed
            return false
          }
        }
      }
      is StackContainerAnimation.Pop -> {
        // already removed
        return false
      }
      is StackContainerAnimation.Push -> {
        val pop = StackContainerAnimation.Pop(topElement.elementWrapper)
        _animatingElements.add(pop)
        return true
      }
      is StackContainerAnimation.Remove -> {
        // already removed
        return false
      }
      is StackContainerAnimation.Set -> {
        _addedElementWrappers
          .removeIfKt { it.elementWrapper.key == topElement.elementWrapper.key }
        return true
      }
    }
  }

  internal fun onAnimationFinished() {
    if (animatingElements.isEmpty()) {
      return
    }

    Snapshot.withMutableSnapshot {
      for (animatingElement in animatingElements) {
        when (animatingElement) {
          is StackContainerAnimation.Fade -> {
            when (animatingElement.fadeType) {
              is StackContainerAnimation.FadeType.In -> {
                _addedElementWrappers.add(animatingElement)
              }
              is StackContainerAnimation.FadeType.Out -> {
                if (animatingElement.fadeType.isRemoving) {
                  _addedElementWrappers
                    .removeIfKt { it.elementWrapper.key == animatingElement.elementWrapper.key }
                } else {
                  _addedElementWrappers.add(animatingElement)
                }
              }
            }
          }
          is StackContainerAnimation.Pop -> {
            _addedElementWrappers
              .removeIfKt { it.elementWrapper.key == animatingElement.elementWrapper.key }
          }
          is StackContainerAnimation.Push -> {
            _addedElementWrappers.add(animatingElement)
          }
          is StackContainerAnimation.Remove -> {
            _addedElementWrappers
              .removeIfKt { it.elementWrapper.key == animatingElement.elementWrapper.key }
          }
          is StackContainerAnimation.Set -> {
            _addedElementWrappers.add(animatingElement)
          }
        }
      }

      _animatingElements.clear()
    }
  }

}

sealed class StackContainerAnimation<T> {
  abstract val elementWrapper: StackContainerElementWrapper<T>

  data class Set<T>(
    override val elementWrapper: StackContainerElementWrapper<T>
  ) : StackContainerAnimation<T>() {
    override fun toString(): String = "Set(key=${elementWrapper.key})"
  }

  data class Remove<T>(
    override val elementWrapper: StackContainerElementWrapper<T>
  ) : StackContainerAnimation<T>() {
    override fun toString(): String = "Remove(key=${elementWrapper.key})"
  }

  data class Push<T>(
    override val elementWrapper: StackContainerElementWrapper<T>
  ) : StackContainerAnimation<T>() {
    override fun toString(): String = "Push(key=${elementWrapper.key})"
  }

  data class Pop<T>(
    override val elementWrapper: StackContainerElementWrapper<T>
  ) : StackContainerAnimation<T>() {
    override fun toString(): String = "Pop(key=${elementWrapper.key})"
  }

  data class Fade<T>(
    override val elementWrapper: StackContainerElementWrapper<T>,
    val fadeType: FadeType
  ) : StackContainerAnimation<T>() {
    override fun toString(): String = "Fade(key=${elementWrapper.key}, fadeType=$fadeType)"
  }

  sealed class FadeType {
    object In : FadeType() {
      override fun toString(): String = "In"
    }

    data class Out(val isRemoving: Boolean) : FadeType() {
      override fun toString(): String = "Out(isRemoving=$isRemoving))"
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StackContainerAnimation<*>

    if (elementWrapper.key != other.elementWrapper.key) return false

    return true
  }

  override fun hashCode(): Int {
    return elementWrapper.key.hashCode()
  }

}
