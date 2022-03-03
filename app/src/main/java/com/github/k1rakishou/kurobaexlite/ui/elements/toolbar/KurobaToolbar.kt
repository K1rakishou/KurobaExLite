package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.AnimateableStackContainer
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomTextField
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.SimpleStackContainerElement
import com.github.k1rakishou.kurobaexlite.ui.helpers.consumeClicks
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberAnimateableStackContainerState
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

private const val searchQuery = "search_query"

enum class PostsScreenToolbarType(val id: Int) {
  Normal(0),
  Search(1)
}

class LeftIconInfo(val drawableId: Int)
class MiddlePartInfo(val centerContent: Boolean = false)
class PostScreenToolbarInfo(val isCatalogScreen: Boolean)

class KurobaToolbarState(
  val leftIconInfo: LeftIconInfo?,
  val middlePartInfo: MiddlePartInfo,
  val postScreenToolbarInfo: PostScreenToolbarInfo? = null
) {
  val toolbarTitleState = mutableStateOf<String?>(null)
}

@Composable
fun KurobaToolbar(
  screenKey: ScreenKey,
  componentActivity: ComponentActivity,
  kurobaToolbarState: KurobaToolbarState,
  navigationRouter: NavigationRouter,
  canProcessBackEvent: () -> Boolean,
  onLeftIconClicked: () -> Unit,
  onMiddleMenuClicked: (() -> Unit)?,
  onSearchQueryUpdated: ((String?) -> Unit)?,
  onToolbarOverflowMenuClicked: (() -> Unit)?
) {
  val chanTheme = LocalChanTheme.current
  val parentBgColor = chanTheme.primaryColorCompose

  val stackContainerState = rememberAnimateableStackContainerState<PostsScreenToolbarType>(
    screenKey = screenKey,
    componentActivity = componentActivity,
    initialValues = listOf(
      SimpleStackContainerElement(
        element = PostsScreenToolbarType.Normal,
        keyExtractor = { it.id }
      )
    )
  )

  navigationRouter.HandleBackPresses(screenKey = screenKey) {
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

  AnimateableStackContainer<PostsScreenToolbarType>(stackContainerState) { postsScreenToolbarType ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .consumeClicks()
        .background(bgColor),
      contentAlignment = Alignment.Center
    ) {
      when (postsScreenToolbarType) {
        PostsScreenToolbarType.Normal -> {
          PostsScreenNormalToolbar(
            kurobaToolbarState = kurobaToolbarState,
            parentBgColor = parentBgColor,
            hasSearchIcon = onSearchQueryUpdated != null,
            onLeftIconClicked = onLeftIconClicked,
            onMiddleMenuClicked = onMiddleMenuClicked,
            onToolbarSearchClicked = {
              stackContainerState.fadeIn(
                SimpleStackContainerElement(
                  element = PostsScreenToolbarType.Search,
                  keyExtractor = { it.id }
                )
              )
            },
            onToolbarOverflowMenuClicked = onToolbarOverflowMenuClicked
          )
        }
        PostsScreenToolbarType.Search -> {
          PostsScreenSearchToolbar(
            parentBgColor = parentBgColor,
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
  parentBgColor: Color,
  hasSearchIcon: Boolean,
  onLeftIconClicked: () -> Unit,
  onMiddleMenuClicked: (() -> Unit)? = null,
  onToolbarSearchClicked: (() -> Unit)? = null,
  onToolbarOverflowMenuClicked: (() -> Unit)? = null
) {
  val leftToolbarPart = leftToolbarPartBuilder(
    kurobaToolbarState = kurobaToolbarState,
    onLeftIconClicked = onLeftIconClicked
  )

  KurobaToolbarLayout(
    leftPart = leftToolbarPart,
    middlePart = {
      val toolbarTitle by kurobaToolbarState.toolbarTitleState
      if (toolbarTitle != null) {
        val horizontalAlignment = if (kurobaToolbarState.middlePartInfo.centerContent) {
          Arrangement.Center
        } else {
          Arrangement.Start
        }

        val clickableModifier = if (onMiddleMenuClicked != null) {
          Modifier.kurobaClickable(onClick = onMiddleMenuClicked)
        } else {
          Modifier
        }

        Row(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .then(clickableModifier),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = horizontalAlignment
        ) {
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
      }
    },
    rightPart = {
      Row {
        if (hasSearchIcon) {
          KurobaComposeIcon(
            modifier = Modifier
              .size(24.dp)
              .kurobaClickable(
                bounded = false,
                onClick = onToolbarSearchClicked
              ),
            drawableId = R.drawable.ic_baseline_search_24,
            colorBehindIcon = parentBgColor
          )

          Spacer(modifier = Modifier.width(4.dp))
        }

        if (onToolbarOverflowMenuClicked != null) {
          KurobaComposeIcon(
            modifier = Modifier
              .size(24.dp)
              .kurobaClickable(
                bounded = false,
                onClick = onToolbarOverflowMenuClicked
              ),
            drawableId = R.drawable.ic_baseline_more_vert_24,
            colorBehindIcon = parentBgColor
          )
        }
      }
    }
  )
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
          .size(24.dp)
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
  onSearchQueryUpdated: ((String?) -> Unit)?,
  onCloseSearchClicked: () -> Unit
) {
  val keyboardController = LocalSoftwareKeyboardController.current

  KurobaToolbarLayout(
    leftPart = {
      KurobaComposeIcon(
        modifier = Modifier
          .size(24.dp)
          .kurobaClickable(
            bounded = false,
            onClick = {
              onSearchQueryUpdated?.invoke(null)
              onCloseSearchClicked()
            }
          ),
        drawableId = R.drawable.ic_baseline_clear_24
      )
    },
    middlePart = {
      var searchQuery by rememberSaveable(key = searchQuery) { mutableStateOf<String>("") }
      val focusRequester = remember { FocusRequester() }

      LaunchedEffect(
        key1 = Unit,
        block = {
          if (searchQuery.isEmpty()) {
            return@LaunchedEffect
          }

          onSearchQueryUpdated?.invoke(searchQuery)
        })

      KurobaComposeCustomTextField(
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth()
          .focusable()
          .focusRequester(focusRequester),
        value = searchQuery,
        labelText = stringResource(R.string.toolbar_type_to_search_hint),
        parentBackgroundColor = parentBgColor,
        onValueChange = { updatedQuery ->
          if (searchQuery != updatedQuery) {
            searchQuery = updatedQuery
            onSearchQueryUpdated?.invoke(updatedQuery)
          }
        }
      )

      val coroutineScope = rememberCoroutineScope()

      DisposableEffect(
        key1 = Unit,
        effect = {
          onSearchQueryUpdated?.invoke("")

          val focusRequestJob = coroutineScope.launch {
            // For some reason without the delay focusRequester doesn't do anything
            awaitFrame()
            focusRequester.requestFocus()
          }

          onDispose {
            onSearchQueryUpdated?.invoke(null)
            keyboardController?.hide()
            focusRequestJob.cancel()
          }
        })
    },
    rightPart = {
    }
  )
}
