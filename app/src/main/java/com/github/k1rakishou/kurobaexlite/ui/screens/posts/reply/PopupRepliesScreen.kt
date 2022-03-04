package com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastSumBy
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.PostListOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PopupRepliesScreen(
  private val replyViewMode: ReplyViewMode,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val popupRepliesScreenViewModel: PopupRepliesScreenViewModel by componentActivity.viewModel()

  private val postListLayoutId = "PopupRepliesScreen_PostListContent"
  private val buttonsLayoutId = "PopupRepliesScreen_Buttons"
  private val buttonsHeight = 50.dp

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun FloatingContent() {
    val postListOptions = remember {
      PostListOptions(
        isCatalogMode = false,
        isInPopup = true,
        contentPadding = PaddingValues(),
        mainUiLayoutMode = MainUiLayoutMode.Portrait
      )
    }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val buttonsHeightPx = with(density) { remember(key1 = buttonsHeight) { buttonsHeight.toPx().toInt() } }

    Layout(
      content = { BuildPopupRepliesScreenContent(postListOptions, coroutineScope) },
      measurePolicy = { measurables, constraints ->
        val placeables = measurables.map { measurable ->
          when (measurable.layoutId) {
            postListLayoutId -> {
              measurable.measure(constraints.offset(vertical = -buttonsHeightPx))
            }
            buttonsLayoutId -> {
              measurable.measure(constraints.copy(minHeight = buttonsHeightPx, maxHeight = buttonsHeightPx))
            }
            else -> error("Unexpected layoutId: \'${measurable.layoutId}\'")
          }
        }

        val width = placeables.fastMap { it.width }.fastMaxBy { it } ?: 0
        val height = placeables.fastSumBy { it.height }

        layout(width, height) {
          var takenHeight = 0

          placeables.fastForEach {
            it.placeRelative(0, takenHeight)
            takenHeight += it.height
          }
        }
      }
    )

    LaunchedEffect(
      key1 = replyViewMode,
      block = { popupRepliesScreenViewModel.loadRepliesForModeInitial(replyViewMode) }
    )
  }

  @Composable
  private fun BuildPopupRepliesScreenContent(
    postListOptions: PostListOptions,
    coroutineScope: CoroutineScope
  ) {
    val chanTheme = LocalChanTheme.current

    Box(modifier = Modifier.layoutId(postListLayoutId)) {
      PostListContent(
        postListOptions = postListOptions,
        postsScreenViewModel = popupRepliesScreenViewModel,
        onPostCellClicked = { postData ->
        },
        onLinkableClicked = { postData, linkable ->
          coroutineScope.launch { processClickedLinkable(linkable) }
        },
        onPostRepliesClicked = { postDescriptor ->
          coroutineScope.launch {
            popupRepliesScreenViewModel.loadRepliesForMode(ReplyViewMode.RepliesFrom(postDescriptor))
          }
        },
        onPostListScrolled = { delta ->
        },
        onPostListTouchingTopOrBottomStateChanged = { touching ->
        },
        onPostListDragStateChanged = { dragging ->
        },
        onFastScrollerDragStateChanged = { dragging ->
        },
      )
    }

    Row(
      modifier = Modifier
        .layoutId(buttonsLayoutId)
        .fillMaxWidth()
        .height(buttonsHeight)
        .padding(vertical = 4.dp)
    ) {
      val textColor = if (ThemeEngine.isDarkColor(chanTheme.backColorCompose)) {
        Color.LightGray
      } else {
        Color.DarkGray
      }

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeTextBarButton(
        modifier = Modifier
          .fillMaxHeight()
          .weight(0.5f),
        text = stringResource(id = R.string.back),
        customTextColor = textColor,
        onClick = { coroutineScope.launch { onBackPressed() } }
      )

      Spacer(modifier = Modifier.width(16.dp))

      KurobaComposeTextBarButton(
        modifier = Modifier
          .fillMaxHeight()
          .weight(0.5f),
        customTextColor = textColor,
        text = stringResource(id = R.string.close),
        onClick = { stopPresenting() }
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  private suspend fun processClickedLinkable(
    linkable: PostCommentParser.TextPartSpan.Linkable
  ) {
    when (linkable) {
      is PostCommentParser.TextPartSpan.Linkable.Quote -> {
        val replyTo = ReplyViewMode.ReplyTo(linkable.postDescriptor)
        popupRepliesScreenViewModel.loadRepliesForMode(replyTo)
      }
      is PostCommentParser.TextPartSpan.Linkable.Board -> {
        // TODO()
      }
      is PostCommentParser.TextPartSpan.Linkable.Search -> {
        // TODO()
      }
      is PostCommentParser.TextPartSpan.Linkable.Url -> {
        // TODO()
      }
    }
  }

  override suspend fun onBackPressed(): Boolean {
    if (popupRepliesScreenViewModel.popReplyChain()) {
      return true
    }

    return super.onBackPressed()
  }

  override fun onDestroy() {
    popupRepliesScreenViewModel.clearPostReplyChainStack()
  }

  sealed class ReplyViewMode {
    data class ReplyTo(
      val postDescriptor: PostDescriptor
    ) : ReplyViewMode()

    data class RepliesFrom(
      val postDescriptor: PostDescriptor
    ) : ReplyViewMode()
  }

  companion object {
    private val SCREEN_KEY = ScreenKey("PopupRepliesScreen")
  }

}