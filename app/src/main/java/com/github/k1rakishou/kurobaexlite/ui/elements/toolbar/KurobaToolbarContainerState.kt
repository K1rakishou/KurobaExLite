package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.AnimateableStackContainerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.SimpleStackContainerElement

@Stable
class KurobaToolbarContainerState<T : KurobaChildToolbar>(
  val key: Any
) {
  private val backPressHandlers = mutableListOf<MainNavigationRouter.OnBackPressHandler>()
  private val callbacksToInvoke = mutableListOf<(AnimateableStackContainerState<T>) -> Unit>()

  private lateinit var _stackContainerState: AnimateableStackContainerState<T>

  fun init(stackContainerState: AnimateableStackContainerState<T>) {
    _stackContainerState = stackContainerState

    callbacksToInvoke.fastForEach { callback -> callback.invoke(stackContainerState) }
    callbacksToInvoke.clear()
  }

  fun setToolbar(childToolbar: T) {
    val func = { containerState: AnimateableStackContainerState<T> ->
      containerState.setIfEmpty(SimpleStackContainerElement(childToolbar))
    }

    if (!::_stackContainerState.isInitialized) {
      callbacksToInvoke += func
    } else {
      func.invoke(_stackContainerState)
    }
  }

  fun fadeInToolbar(childToolbar: T) {
    val func = { containerState: AnimateableStackContainerState<T> ->
      containerState.fadeIn(SimpleStackContainerElement(childToolbar))
    }

    if (!::_stackContainerState.isInitialized) {
      callbacksToInvoke += func
    } else {
      func.invoke(_stackContainerState)
    }
  }

  fun popChildToolbars() {
    if (!::_stackContainerState.isInitialized) {
      return
    }

    _stackContainerState.popAll()
  }

  fun popToolbar(expectedKey: String, withAnimation: Boolean = true) {
    if (!::_stackContainerState.isInitialized) {
      return
    }

    _stackContainerState.removeTop(
      withAnimation = withAnimation,
      predicate = { topToolbarKey -> expectedKey == topToolbarKey }
    )
  }

  fun removeToolbar(expectedKey: String, withAnimation: Boolean = true) {
    if (!::_stackContainerState.isInitialized) {
      return
    }

    _stackContainerState.removeByKey(
      key = expectedKey,
      withAnimation = withAnimation,
    )
  }

  inline fun <reified T : KurobaChildToolbar> topChildToolbar(): T? {
    return topChildToolbarInternal(T::class.java)
  }

  inline fun <reified T : KurobaChildToolbar> childToolbar(key: String): T? {
    return childToolbarInternal(key, T::class.java)
  }

  @PublishedApi
  internal fun <T : KurobaChildToolbar> topChildToolbarInternal(clazz: Class<T>): T? {
    val added = _stackContainerState.addedElements.lastOrNull() as? T
    if (added != null) {
      return added
    }

    val animating = _stackContainerState.animatingElements.lastOrNull() as? T
    if (animating != null) {
      return animating
    }

    return null
  }

  @PublishedApi
  internal fun <T : KurobaChildToolbar> childToolbarInternal(key: String, clazz: Class<T>): T? {
    val added = _stackContainerState.addedElements
      .lastOrNull { element ->
        return@lastOrNull element.elementKey == key &&
          element.element.javaClass == clazz
      } as? T

    if (added != null) {
      return added
    }

    val animating = _stackContainerState.animatingElements
      .lastOrNull { animation ->
        return@lastOrNull animation.stackContainerElement.elementKey == key &&
          animation.stackContainerElement.element.javaClass == clazz
      } as? T
    if (animating != null) {
      return animating
    }

    return null
  }

  @Composable
  fun HandleBackPresses(backPressHandler: MainNavigationRouter.OnBackPressHandler) {
    DisposableEffect(
      key1 = Unit,
      effect = {
        backPressHandlers += backPressHandler
        onDispose { backPressHandlers -= backPressHandler }
      }
    )
  }

  suspend fun onBackPressed(): Boolean {
    for (backPressHandler in backPressHandlers) {
      if (backPressHandler.onBackPressed()) {
        return true
      }
    }

    return false
  }

}