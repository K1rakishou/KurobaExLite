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
  for (stackContainerElement in state.addedElements) {
    val element = stackContainerElement.element

    key(stackContainerElement.elementKey) {
      val movableContent = composedItems.getOrPut(
        key = element,
        defaultValue = { movableContentOf<T> { content(element) } }
      )

      movableContent(element)
    }
  }

  // Then iterate and draw elements which are currently being animated
  for (stackContainerElement in state.animatingElements) {
    val element = stackContainerElement.stackContainerElement.element

    key(stackContainerElement.stackContainerElement.elementKey) {
      val movableContent = composedItems.getOrPut(
        key = element,
        defaultValue = { movableContentOf<T> { content(element) } }
      )

      StackContainerTransition(
        animatingChange = stackContainerElement,
        onAnimationFinished = { state.onAnimationFinished() }
      ) {
        movableContent(element)
      }
    }
  }
}

@Composable
fun <T : DisposableElement> rememberAnimateableStackContainerState(
  toolbarContainerKey: String
): AnimateableStackContainerState<T> {
  val componentActivity = LocalComponentActivity.current
  val viewModel = componentActivity.rememberViewModel<AnimateableStackContainerViewModel>()

  return viewModel.storage.getOrPut(
    key = toolbarContainerKey,
    defaultValue = { AnimateableStackContainerState<T>() }
  ) as AnimateableStackContainerState<T>
}

class AnimateableStackContainerViewModel : ViewModel() {
  internal val storage = mutableMapOf<String, AnimateableStackContainerState<*>>()
}