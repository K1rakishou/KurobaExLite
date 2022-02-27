package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.*

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
  kurobaToolbarState: KurobaToolbarState,
  navigationRouter: NavigationRouter,
  onLeftIconClicked: () -> Unit,
  onMiddleMenuClicked: (() -> Unit)?,
  onSearchQueryUpdated: ((String?) -> Unit)?,
  onToolbarOverflowMenuClicked: (() -> Unit)?
) {
  val chanTheme = LocalChanTheme.current
  val parentBgColor = chanTheme.primaryColorCompose

  val stackContainerState = rememberAnimateableStackContainerState<PostsScreenToolbarType>(
    initialValues = listOf(
      SimpleStackContainerElement(
        element = PostsScreenToolbarType.Normal,
        keyExtractor = { it.id }
      )
    )
  )

  navigationRouter.HandleBackPresses {
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

  DisposableEffect(
    key1 = Unit,
    effect = {
      onDispose {
        onSearchQueryUpdated?.invoke(null)
        keyboardController?.hide()
      }
    })

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
      var searchQuery by remember { mutableStateOf<String>("") }

      KurobaComposeCustomTextField(
        modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth(),
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
    },
    rightPart = {
    }
  )
}
