package com.github.k1rakishou.kurobaexlite.ui.elements.toolbar

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.source.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.*
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.AbstractPostsState

private val toolbarTitleCache = LruCache<ChanDescriptor, String>(16)

enum class PostsScreenToolbarType(val id: Int) {
  Normal(0),
  Search(1)
}

@Composable
fun PostsScreenToolbar(
  isCatalogScreen: Boolean,
  postListAsync: AsyncData<AbstractPostsState>,
  parsedPostDataCache: ParsedPostDataCache,
  uiInfoManager: UiInfoManager,
  navigationRouter: NavigationRouter,
  onLeftIconClicked: () -> Unit,
  onMiddleMenuClicked: () -> Unit,
  onSearchQueryUpdated: (String?) -> Unit,
  onToolbarOverflowMenuClicked: (() -> Unit)? = null
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
            isCatalogScreen = isCatalogScreen,
            parentBgColor = parentBgColor,
            postListAsync = postListAsync,
            parsedPostDataCache = parsedPostDataCache,
            uiInfoManager = uiInfoManager,
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
  isCatalogScreen: Boolean,
  parentBgColor: Color,
  postListAsync: AsyncData<AbstractPostsState>,
  parsedPostDataCache: ParsedPostDataCache,
  uiInfoManager: UiInfoManager,
  onLeftIconClicked: () -> Unit,
  onMiddleMenuClicked: () -> Unit,
  onToolbarSearchClicked: (() -> Unit)? = null,
  onToolbarOverflowMenuClicked: (() -> Unit)? = null
) {
  val configuration = LocalConfiguration.current
  val mainUiLayoutMode = remember(key1 = configuration) { uiInfoManager.mainUiLayoutMode(configuration) }
  val leftToolbarPart = leftToolbarPartBuilder(
    mainUiLayoutMode = mainUiLayoutMode,
    isCatalogScreen = isCatalogScreen,
    onLeftIconClicked = onLeftIconClicked
  )

  KurobaToolbarLayout(
    leftPart = leftToolbarPart,
    middlePart = {
      var toolbarTitle by remember {
        val chanDescriptor = (postListAsync as? AsyncData.Data)?.data?.chanDescriptor

        val initialValue = if (chanDescriptor != null) {
          toolbarTitleCache[chanDescriptor]
        } else {
          null
        }

        return@remember mutableStateOf<String?>(initialValue)
      }

      when (postListAsync) {
        AsyncData.Empty -> {
          // no-op
        }
        AsyncData.Loading -> toolbarTitle = stringResource(R.string.toolbar_loading_title)
        is AsyncData.Error -> toolbarTitle = stringResource(R.string.toolbar_loading_subtitle)
        is AsyncData.Data -> {
          LaunchedEffect(
            key1 = isCatalogScreen,
            key2 = postListAsync,
            block = {
              val postListState = postListAsync.data.posts.firstOrNull()
                ?: return@LaunchedEffect

              val originalPost by postListState
              val chanDescriptor = postListAsync.data.chanDescriptor

              toolbarTitle = parsedPostDataCache.formatToolbarTitle(
                chanDescriptor = chanDescriptor,
                postDescriptor = originalPost.postDescriptor,
                catalogMode = isCatalogScreen
              )

              toolbarTitleCache.put(chanDescriptor, toolbarTitle)
            })
        }
      }

      if (toolbarTitle != null) {
        val isDataLoaded = postListAsync is AsyncData.Data

        val clickModifier = if (isDataLoaded && isCatalogScreen) {
          Modifier.kurobaClickable(onClick = onMiddleMenuClicked)
        } else {
          Modifier
        }

        val horizontalAlignment = if (isCatalogScreen) {
          Arrangement.Center
        } else {
          Arrangement.Start
        }

        Row(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .then(clickModifier),
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

          if (isDataLoaded && isCatalogScreen) {
            Spacer(modifier = Modifier.width(8.dp))

            KurobaComposeIcon(drawableId = R.drawable.ic_baseline_keyboard_arrow_down_24)

            Spacer(modifier = Modifier.width(8.dp))
          }
        }
      }
    },
    rightPart = {
      Row {
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
  )
}

private fun leftToolbarPartBuilder(
  mainUiLayoutMode: MainUiLayoutMode,
  isCatalogScreen: Boolean,
  onLeftIconClicked: () -> Unit
): @Composable (BoxScope.() -> Unit)? {
  if (mainUiLayoutMode == MainUiLayoutMode.Split && !isCatalogScreen) {
    return null
  }

  val func: @Composable (BoxScope.() -> Unit) = {
    Box {
      val iconDrawableId = if (isCatalogScreen) {
        R.drawable.ic_baseline_dehaze_24
      } else {
        R.drawable.ic_baseline_arrow_back_24
      }

      KurobaComposeIcon(
        modifier = Modifier
          .size(24.dp)
          .kurobaClickable(
            bounded = false,
            onClick = { onLeftIconClicked() }
          ),
        drawableId = iconDrawableId
      )
    }
  }

  return func
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BoxScope.PostsScreenSearchToolbar(
  parentBgColor: Color,
  onSearchQueryUpdated: (String?) -> Unit,
  onCloseSearchClicked: () -> Unit
) {
  val keyboardController = LocalSoftwareKeyboardController.current

  DisposableEffect(
    key1 = Unit,
    effect = {
      onDispose {
        onSearchQueryUpdated(null)
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
              onSearchQueryUpdated(null)
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
            onSearchQueryUpdated(updatedQuery)
          }
        }
      )
    },
    rightPart = {
    }
  )
}
