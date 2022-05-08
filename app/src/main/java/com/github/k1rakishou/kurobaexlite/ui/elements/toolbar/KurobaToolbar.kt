package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.executors.DebouncingCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.AnimateableStackContainer
import com.github.k1rakishou.kurobaexlite.ui.helpers.AnimateableStackContainerState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.SimpleStackContainerElement
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberAnimateableStackContainerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val searchQueryKey = "search_query"

enum class ToolbarType(val id: Int) {
  Normal(0),
  Search(1)
}

class LeftIconInfo(val drawableId: Int)
class MiddlePartInfo(val centerContent: Boolean = false)
class RightPartInfo(vararg val toolbarIcons: ToolbarIcon)
class PostScreenToolbarInfo(val isCatalogScreen: Boolean)

class ToolbarIcon(
  val key: Any,
  @DrawableRes drawableId: Int,
  visible: Boolean = true
) {
  val iconVisible = mutableStateOf(visible)
  val drawableId = mutableStateOf(drawableId)
}

private val toolbarIconSize = 30.dp

@Stable
class KurobaToolbarState(
  val leftIconInfo: LeftIconInfo?,
  val middlePartInfo: MiddlePartInfo,
  val rightPartInfo: RightPartInfo? = null,
  val postScreenToolbarInfo: PostScreenToolbarInfo? = null
) {
  val toolbarTitleState = mutableStateOf<String?>(null)
  val toolbarSubtitleState = mutableStateOf<String?>(null)
  private val backPressHandlers = mutableListOf<MainNavigationRouter.OnBackPressHandler>()

  private val _popChildToolbarsEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.UNLIMITED)
  val popChildToolbarsEventFlow: SharedFlow<Unit>
    get() = _popChildToolbarsEventFlow.asSharedFlow()

  private val _childToolbarsEventFlow = MutableSharedFlow<ToolbarType>(extraBufferCapacity = Channel.UNLIMITED)
  val childToolbarsEventFlow: SharedFlow<ToolbarType>
    get() = _childToolbarsEventFlow.asSharedFlow()

  private val _toolbarIconClickEventFlow = MutableSharedFlow<Any>(extraBufferCapacity = Channel.UNLIMITED)
  val toolbarIconClickEventFlow: SharedFlow<Any>
    get() = _toolbarIconClickEventFlow.asSharedFlow()

  fun rightIconByKey(key: Any): ToolbarIcon? {
    return rightPartInfo?.toolbarIcons
      ?.firstOrNull { toolbarIcon -> toolbarIcon.key == key }
  }

  fun onToolbarIconClicked(key: Any) {
    _toolbarIconClickEventFlow.tryEmit(key)
  }

  fun openSearch() {
    _childToolbarsEventFlow.tryEmit(ToolbarType.Search)
  }

  fun popChildToolbars() {
    _popChildToolbarsEventFlow.tryEmit(Unit)
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
fun KurobaToolbar(
  screenKey: ScreenKey,
  kurobaToolbarState: KurobaToolbarState,
  canProcessBackEvent: () -> Boolean,
  onLeftIconClicked: () -> Unit,
  onMiddleMenuClicked: (() -> Unit)?,
  onSearchQueryUpdated: ((String?) -> Unit)?,
) {
  val chanTheme = LocalChanTheme.current
  val parentBgColor = chanTheme.primaryColorCompose

  val stackContainerState = rememberAnimateableStackContainerState<ToolbarType>(
    screenKey = screenKey,
    initialValues = listOf(
      SimpleStackContainerElement(
        element = ToolbarType.Normal,
        keyExtractor = { it.id }
      )
    )
  )

  kurobaToolbarState.HandleBackPresses {
    if (!canProcessBackEvent()) {
      return@HandleBackPresses false
    }

    if (stackContainerState.addedElementsCount > 1) {
      stackContainerState.removeTop(withAnimation = true)
      return@HandleBackPresses true
    }

    return@HandleBackPresses false
  }

  LaunchedEffect(
    key1 = Unit,
    block = {
      kurobaToolbarState.popChildToolbarsEventFlow.collect {
        stackContainerState.popTillRoot()
      }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      kurobaToolbarState.childToolbarsEventFlow.collect {
        stackContainerState.fadeIn(
          SimpleStackContainerElement(
            element = ToolbarType.Search,
            keyExtractor = { it.id }
          )
        )
      }
    }
  )

  val bgColor = if (stackContainerState.addedElementsCount <= 1) {
    Color.Unspecified
  } else {
    chanTheme.primaryColorCompose
  }

  AnimateableStackContainer<ToolbarType>(stackContainerState) { postsScreenToolbarType ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(bgColor),
      contentAlignment = Alignment.Center
    ) {
      when (postsScreenToolbarType) {
        ToolbarType.Normal -> {
          PostsScreenNormalToolbar(
            kurobaToolbarState = kurobaToolbarState,
            onLeftIconClicked = onLeftIconClicked,
            onMiddleMenuClicked = onMiddleMenuClicked
          )
        }
        ToolbarType.Search -> {
          PostsScreenSearchToolbar(
            parentBgColor = parentBgColor,
            stackContainerState = stackContainerState,
            onSearchQueryUpdated = onSearchQueryUpdated,
            onCloseSearchClicked = { stackContainerState.removeTop(withAnimation = true) }
          )
        }
      }
    }
  }
}

@Composable
private fun BoxScope.PostsScreenNormalToolbar(
  kurobaToolbarState: KurobaToolbarState,
  onLeftIconClicked: () -> Unit,
  onMiddleMenuClicked: (() -> Unit)? = null
) {
  KurobaToolbarLayout(
    leftPart = leftToolbarPartBuilder(
      kurobaToolbarState = kurobaToolbarState,
      onLeftIconClicked = onLeftIconClicked
    ),
    middlePart = {
      val toolbarTitle by kurobaToolbarState.toolbarTitleState
      val toolbarSubtitle by kurobaToolbarState.toolbarSubtitleState

      if (toolbarTitle != null) {
        val horizontalAlignment = if (kurobaToolbarState.middlePartInfo.centerContent) {
          Alignment.CenterHorizontally
        } else {
          Alignment.Start
        }

        val clickableModifier = if (onMiddleMenuClicked != null) {
          Modifier.kurobaClickable(onClick = onMiddleMenuClicked)
        } else {
          Modifier
        }

        Column(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .then(clickableModifier),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = horizontalAlignment
        ) {
          Row {
            Text(
              text = toolbarTitle!!,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              fontSize = 16.sp
            )

            if (kurobaToolbarState.postScreenToolbarInfo?.isCatalogScreen == true) {
              Spacer(modifier = Modifier.width(8.dp))

              KurobaComposeIcon(drawableId = R.drawable.ic_baseline_keyboard_arrow_down_24)

              Spacer(modifier = Modifier.width(8.dp))
            }
          }

          if (toolbarSubtitle != null) {
            Text(
              text = toolbarSubtitle!!,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              fontSize = 12.sp
            )
          }
        }
      }
    },
    rightPart = rightPartBuilder(kurobaToolbarState)
  )
}

fun rightPartBuilder(
  kurobaToolbarState: KurobaToolbarState
): @Composable (BoxScope.() -> Unit)? {
  if (kurobaToolbarState.rightPartInfo?.toolbarIcons?.isEmpty() != false) {
    return null
  }

  val func: @Composable (BoxScope.() -> Unit) = {
    val toolbarIcons = kurobaToolbarState.rightPartInfo.toolbarIcons

    Row {
      for ((index, toolbarIcon) in toolbarIcons.withIndex()) {
        val iconVisible by toolbarIcon.iconVisible
        if (iconVisible) {
          val drawableId by toolbarIcon.drawableId

          key(toolbarIcon.key) {
            KurobaComposeIcon(
              modifier = Modifier
                .size(toolbarIconSize)
                .kurobaClickable(
                  bounded = false,
                  onClick = { kurobaToolbarState.onToolbarIconClicked(toolbarIcon.key) }
                ),
              drawableId = drawableId
            )

            if (index != toolbarIcons.lastIndex) {
              Spacer(modifier = Modifier.width(10.dp))
            }
          }
        }
      }
    }
  }

  return func
}

private fun leftToolbarPartBuilder(
  kurobaToolbarState: KurobaToolbarState,
  onLeftIconClicked: () -> Unit
): @Composable (BoxScope.() -> Unit)? {
  if (kurobaToolbarState.leftIconInfo == null) {
    return null
  }

  val func: @Composable (BoxScope.() -> Unit) = {
    Box {
      KurobaComposeIcon(
        modifier = Modifier
          .size(toolbarIconSize)
          .kurobaClickable(
            bounded = false,
            onClick = { onLeftIconClicked() }
          ),
        drawableId = kurobaToolbarState.leftIconInfo.drawableId
      )
    }
  }

  return func
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BoxScope.PostsScreenSearchToolbar(
  parentBgColor: Color,
  stackContainerState: AnimateableStackContainerState<ToolbarType>,
  onSearchQueryUpdated: ((String?) -> Unit)?,
  onCloseSearchClicked: () -> Unit
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val coroutineScope = rememberCoroutineScope()
  val searchDebouncer = remember { DebouncingCoroutineExecutor(coroutineScope) }

  var searchQuery by remember {
    val prevSearchQuery = stackContainerState.readData(searchQueryKey) ?: ""
    return@remember mutableStateOf(TextFieldValue(text = prevSearchQuery))
  }

  DisposableEffect(
    key1 = Unit,
    effect = {
      onSearchQueryUpdated?.invoke(searchQuery.text)

      onDispose {
        onSearchQueryUpdated?.invoke(null)
        keyboardController?.hide()
      }
    }
  )

  KurobaToolbarLayout(
    leftPart = {
      KurobaComposeIcon(
        modifier = Modifier
          .size(toolbarIconSize)
          .kurobaClickable(
            bounded = false,
            onClick = {
              stackContainerState.removeData(searchQueryKey)

              if (searchQuery.text.isNotEmpty()) {
                searchQuery = TextFieldValue(text = "")
                onSearchQueryUpdated?.invoke("")
              } else {
                searchDebouncer.stop()
                onSearchQueryUpdated?.invoke(null)
                onCloseSearchClicked()
              }
            }
          ),
        drawableId = if (searchQuery.text.isNotEmpty()) {
          R.drawable.ic_baseline_clear_24
        } else {
          R.drawable.ic_baseline_arrow_back_24
        }
      )
    },
    middlePart = {
      KurobaComposeCustomTextField(
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth()
          .focusable(),
        value = searchQuery,
        labelText = stringResource(R.string.toolbar_type_to_search_hint),
        parentBackgroundColor = parentBgColor,
        onValueChange = { updatedQuery ->
          if (searchQuery != updatedQuery) {
            searchQuery = updatedQuery
            stackContainerState.storeData(searchQueryKey, updatedQuery.text)

            searchDebouncer.post(timeout = 125L) {
              onSearchQueryUpdated?.invoke(updatedQuery.text)
            }
          }
        }
      )
    },
    rightPart = null
  )
}
