package com.github.k1rakishou.kurobaexlite.ui.screens.posts.reply

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastSumBy
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.ui.screens.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.LinkableClickHelper
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PopupRepliesScreen(
  private val replyViewMode: ReplyViewMode,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val popupRepliesScreenViewModel: PopupRepliesScreenViewModel by componentActivity.viewModel()

  private val postListLayoutId = "PopupRepliesScreen_PostListContent"
  private val buttonsLayoutId = "PopupRepliesScreen_Buttons"
  private val buttonsHeight = 50.dp

  private val linkableClickHelper by lazy {
    LinkableClickHelper(componentActivity, navigationRouter)
  }

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun FloatingContent() {
    val postCellCommentTextSizeSp by uiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by uiInfoManager.postCellSubjectTextSizeSp.collectAsState()

    val postListOptions by remember {
      derivedStateOf {
        PostListOptions(
          isCatalogMode = false,
          isInPopup = true,
          pullToRefreshEnabled = false,
          contentPadding = PaddingValues(),
          mainUiLayoutMode = MainUiLayoutMode.Portrait,
          postCellCommentTextSizeSp = postCellCommentTextSizeSp,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
          detectLinkableClicks = true
        )
      }
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
    val context = LocalContext.current

    Box(
      modifier = Modifier
        .layoutId(postListLayoutId)
    ) {
      PostListContent(
        postListOptions = postListOptions,
        postsScreenViewModel = popupRepliesScreenViewModel,
        onPostCellClicked = { postCellData ->
        },
        onLinkableClicked = { postCellData, linkable ->
          linkableClickHelper.processClickedLinkable(
            context = context,
            postCellData = postCellData,
            linkable = linkable,
            loadThreadFunc = { threadDescriptor ->
              threadScreenViewModel.loadThread(threadDescriptor)
              stopPresenting()
            },
            loadCatalogFunc = { catalogDescriptor ->
              catalogScreenViewModel.loadCatalog(catalogDescriptor)
              stopPresenting()
            },
            showRepliesForPostFunc = { replyViewMode ->
              coroutineScope.launch {
                popupRepliesScreenViewModel.loadRepliesForMode(replyViewMode)
              }
            }
          )
        },
        onPostRepliesClicked = { postDescriptor ->
          coroutineScope.launch {
            popupRepliesScreenViewModel.loadRepliesForMode(ReplyViewMode.RepliesFrom(postDescriptor))
          }
        },
        onPostImageClicked = { _, postImageData ->
          val collectedImages = popupRepliesScreenViewModel.collectCurrentImages()
          if (collectedImages.isEmpty()) {
            return@PostListContent
          }

          val mediaViewerScreen = MediaViewerScreen(
            mediaViewerParams = MediaViewerParams.Images(
              images = collectedImages,
              initialImageUrl = postImageData.fullImageAsUrl
            ),
            componentActivity = componentActivity,
            navigationRouter = navigationRouter
          )

          navigationRouter.presentScreen(mediaViewerScreen)
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

      Spacer(modifier = Modifier.width(4.dp))

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

  override suspend fun onBackPressed(): Boolean {
    if (popupRepliesScreenViewModel.popReplyChain()) {
      return true
    }

    return super.onBackPressed()
  }

  override fun onDestroy() {
    cardAlphaState.value = 0f
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