package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.AnimateableStackContainer
import com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack.rememberAnimateableStackContainerState

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
    backgroundColor ?: chanTheme.primaryColorCompose
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