package com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.github.k1rakishou.kurobaexlite.helpers.rememberViewModel
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalComponentActivity

@Composable
fun <T : DisposableElement> AnimateableStackContainer(
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
  for (animatingChange in state.animatingChanges) {
    val element = animatingChange.elementWrapper.element

    key(animatingChange.elementWrapper.key) {
      val movableContent = composedItems.getOrPut(
        key = element,
        defaultValue = { movableContentOf<T> { content(element) } }
      )

      StackContainerTransition(
        animatingChange = animatingChange,
        onAnimationFinished = { state.onAnimationFinished() }
      ) {
        movableContent(element)
      }
    }
  }
}

@Composable
fun <T : DisposableElement> rememberAnimateableStackContainerState(
  key: Any,
  initialValues: List<StackContainerElementWrapper<T>> = emptyList()
): AnimateableStackContainerState<T> {
  val componentActivity = LocalComponentActivity.current
  val viewModel = componentActivity.rememberViewModel<AnimateableStackContainerViewModel>()

  return viewModel.storage.getOrPut(
    key = key,
    defaultValue = {
      return@getOrPut AnimateableStackContainerState(
        initialValues = initialValues,
        key = key
      )
    }
  ) as AnimateableStackContainerState<T>
}

class AnimateableStackContainerViewModel : ViewModel() {
  internal val storage = mutableMapOf<Any, AnimateableStackContainerState<*>>()
}