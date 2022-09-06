package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.AnimateableStackContainer
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.rememberAnimateableStackContainerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks

@Composable
fun <T : KurobaChildToolbar> KurobaToolbarContainer(
  toolbarContainerKey: String,
  backgroundColor: Color? = null,
  kurobaToolbarContainerState: KurobaToolbarContainerState<T>,
  canProcessBackEvent: () -> Boolean
) {
  val chanTheme = LocalChanTheme.current

  val stackContainerState = rememberAnimateableStackContainerState<T>(toolbarContainerKey)
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
    backgroundColor ?: chanTheme.backColor
  }

  AnimateableStackContainer<T>(stackContainerState) { childToolbar ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawBehind { drawRect(bgColor) }
        .consumeClicks(enabled = true),
      contentAlignment = Alignment.Center
    ) {
      childToolbar.Content()
    }
  }
}

class KurobaToolbarContainerViewModel : ViewModel() {
  private val kurobaToolbarContainerStateMap = mutableMapOf<Any, KurobaToolbarContainerState<KurobaChildToolbar>>()

  fun <T : KurobaChildToolbar> getOrCreate(
    key: Any
  ): KurobaToolbarContainerState<T> {
    return kurobaToolbarContainerStateMap.getOrPut(
      key = key,
      defaultValue = { KurobaToolbarContainerState() }
    ) as KurobaToolbarContainerState<T>
  }
}