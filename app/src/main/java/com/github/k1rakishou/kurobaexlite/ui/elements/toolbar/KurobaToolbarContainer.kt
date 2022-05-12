package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.AnimateableStackContainer
import com.github.k1rakishou.kurobaexlite.ui.helpers.AnimateableStackContainerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.DisposableElement
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.SimpleStackContainerElement
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberAnimateableStackContainerState

abstract class ChildToolbar : DisposableElement {
  abstract val toolbarKey: String

  @Composable
  abstract fun Content()


  override fun onDispose() {

  }

  companion object {
    val toolbarIconSize = 30.dp
  }
}

class ToolbarIcon<T : Any>(
  val key: T,
  @DrawableRes drawableId: Int,
  visible: Boolean = true
) {
  val visible = mutableStateOf(visible)
  val drawableId = mutableStateOf(drawableId)

  @Composable
  fun Content(onClick: (T) -> Unit) {
    val iconVisible by visible
    if (!iconVisible) {
      return
    }

    val iconDrawableId by drawableId

    KurobaComposeIcon(
      modifier = Modifier
        .padding(horizontal = 4.dp)
        .size(ChildToolbar.toolbarIconSize)
        .kurobaClickable(
          bounded = false,
          onClick = { onClick(key) }
        ),
      drawableId = iconDrawableId
    )
  }
}

@Stable
class KurobaToolbarContainerState<T : ChildToolbar> {
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
      containerState.set(
        SimpleStackContainerElement(
          element = childToolbar,
          keyExtractor = { it.toolbarKey }
        )
      )
    }

    if (!::_stackContainerState.isInitialized) {
      callbacksToInvoke += func
    } else {
      func.invoke(_stackContainerState)
    }
  }

  fun fadeInToolbar(childToolbar: T) {
    val func = { containerState: AnimateableStackContainerState<T> ->
      containerState.fadeIn(
        SimpleStackContainerElement(
          element = childToolbar,
          keyExtractor = { it.toolbarKey }
        )
      )
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

  fun popToolbar(expectedKey: String) {
    if (!::_stackContainerState.isInitialized) {
      return
    }

    _stackContainerState.removeTop(
      withAnimation = true,
      predicate = { topToolbarKey -> expectedKey == topToolbarKey }
    )
  }

  inline fun <reified T : ChildToolbar> topChildToolbar(): T? {
    return topChildToolbarInternal(T::class.java)
  }

  inline fun <reified T : ChildToolbar> childToolbar(key: Any): T? {
    return childToolbarInternal(key, T::class.java)
  }

  @PublishedApi
  internal fun <T : ChildToolbar> topChildToolbarInternal(clazz: Class<T>): T? {
    val added = _stackContainerState.addedElementWrappers.lastOrNull() as? T
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
  internal fun <T : ChildToolbar> childToolbarInternal(key: Any, clazz: Class<T>): T? {
    val added = _stackContainerState.addedElementWrappers
      .lastOrNull { wrapper -> wrapper.key == key && wrapper.element.javaClass == clazz } as? T
    if (added != null) {
      return added
    }

    val animating = _stackContainerState.animatingElements
      .lastOrNull { animation -> animation.elementWrapper.key == key && animation.elementWrapper.element.javaClass == clazz } as? T
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

@Composable
fun <T : ChildToolbar> KurobaToolbarContainer(
  screenKey: ScreenKey,
  kurobaToolbarContainerState: KurobaToolbarContainerState<T>,
  canProcessBackEvent: () -> Boolean
) {
  val chanTheme = LocalChanTheme.current

  val stackContainerState = rememberAnimateableStackContainerState<T>(screenKey)
  kurobaToolbarContainerState.init(stackContainerState)

  kurobaToolbarContainerState.HandleBackPresses {
    if (!canProcessBackEvent()) {
      return@HandleBackPresses false
    }

    if (stackContainerState.addedElementsCount > 1) {
      stackContainerState.removeTop(withAnimation = true)
      return@HandleBackPresses true
    }

    return@HandleBackPresses false
  }

  val bgColor = if (stackContainerState.addedElementsCount <= 1) {
    Color.Unspecified
  } else {
    chanTheme.primaryColorCompose
  }

  AnimateableStackContainer<T>(stackContainerState) { childToolbar ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(bgColor),
      contentAlignment = Alignment.Center
    ) {
      childToolbar.Content()
    }
  }
}
