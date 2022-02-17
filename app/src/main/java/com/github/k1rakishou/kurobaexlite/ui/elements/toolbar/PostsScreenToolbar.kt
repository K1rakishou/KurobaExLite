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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.AsyncData
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
fun BoxScope.PostsScreenToolbar(
  isCatalogScreen: Boolean,
  postListAsync: AsyncData<AbstractPostsState>,
  parsedPostDataCache: ParsedPostDataCache,
  navigationRouter: NavigationRouter,
  onSearchQueryUpdated: (String?, MutableState<Int?>) -> Unit,
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

  navigationRouter.HandleBackPresses{
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
  onToolbarSearchClicked: (() -> Unit)? = null,
  onToolbarOverflowMenuClicked: (() -> Unit)? = null
) {
  KurobaToolbarLayout(
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
        Text(
          text = toolbarTitle!!,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontSize = 16.sp
        )
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BoxScope.PostsScreenSearchToolbar(
  parentBgColor: Color,
  onSearchQueryUpdated: (String?, MutableState<Int?>) -> Unit,
  onCloseSearchClicked: () -> Unit
) {
  val keyboardController = LocalSoftwareKeyboardController.current

  val displayingPostsCountState = remember { mutableStateOf<Int?>(null) }
  val displayingPostsCount by displayingPostsCountState

  DisposableEffect(
    key1 = Unit,
    effect = {
      onDispose {
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
              onSearchQueryUpdated(null, displayingPostsCountState)
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
            onSearchQueryUpdated(updatedQuery, displayingPostsCountState)
          }
        }
      )
    },
    rightPart = {
      val displayingPostsCountText = remember(key1 = displayingPostsCount) {
        if (displayingPostsCount == null) {
          return@remember "???"
        } else {
          return@remember displayingPostsCount.toString()
        }
      }

      KurobaComposeText(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(horizontal = 4.dp),
        text = displayingPostsCountText
      )
    }
  )
}
