package com.github.k1rakishou.kurobaexlite.features.posts.reply

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
import com.github.k1rakishou.kurobaexlite.base.AsyncData
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerParams
import com.github.k1rakishou.kurobaexlite.features.media.MediaViewerScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.LinkableClickHelper
import com.github.k1rakishou.kurobaexlite.features.posts.shared.PostLongtapContentMenu
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListContent
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.managers.MainUiLayoutMode
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class PopupRepliesScreen(
  private val replyViewMode: ReplyViewMode,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(componentActivity, navigationRouter) {
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()
  private val catalogScreenViewModel: CatalogScreenViewModel by componentActivity.viewModel()
  private val popupRepliesScreenViewModel: PopupRepliesScreenViewModel by componentActivity.viewModel()
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private val postListLayoutId = "PopupRepliesScreen_PostListContent"
  private val buttonsLayoutId = "PopupRepliesScreen_Buttons"
  private val buttonsHeight = 50.dp

  private val linkableClickHelper by lazy {
    LinkableClickHelper(componentActivity, navigationRouter)
  }

  private val postLongtapContentMenu by lazy {
    PostLongtapContentMenu(componentActivity, navigationRouter)
  }

  override val screenKey: ScreenKey = SCREEN_KEY
  override val unpresentAnimation: NavigationRouter.ScreenRemoveAnimation = NavigationRouter.ScreenRemoveAnimation.Pop

  @Composable
  override fun FloatingContent() {
    val orientationMut by uiInfoManager.currentOrientation.collectAsState()
    val orientation = orientationMut
    if (orientation == null) {
      return
    }

    val postCellCommentTextSizeSp by uiInfoManager.postCellCommentTextSizeSp.collectAsState()
    val postCellSubjectTextSizeSp by uiInfoManager.postCellSubjectTextSizeSp.collectAsState()
    val postsAsyncDataState by popupRepliesScreenViewModel.postScreenState.postsAsyncDataState.collectAsState()

    val postListOptions by remember {
      derivedStateOf {
        PostListOptions(
          isCatalogMode = false,
          isInPopup = true,
          ownerScreenKey = screenKey,
          pullToRefreshEnabled = false,
          contentPadding = PaddingValues(),
          mainUiLayoutMode = MainUiLayoutMode.Portrait,
          postCellCommentTextSizeSp = postCellCommentTextSizeSp,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
          detectLinkableClicks = true,
          orientation = orientation
        )
      }
    }

    val density = LocalDensity.current
    val buttonsHeightPx = with(density) { remember(key1 = buttonsHeight) { buttonsHeight.toPx().toInt() } }

    if (postsAsyncDataState !is AsyncData.Uninitialized && postsAsyncDataState !is AsyncData.Loading) {
      Layout(
        content = { BuildPopupRepliesScreenContent(postListOptions) },
        measurePolicy = { measurables, constraints ->
          val placeables = measurables.map { measurable ->
            when (measurable.layoutId) {
              postListLayoutId -> {
                measurable.measure(constraints.offset(vertical = -buttonsHeightPx))
              }
              buttonsLayoutId -> {
                measurable.measure(
                  constraints.copy(
                    minHeight = buttonsHeightPx,
                    maxHeight = buttonsHeightPx
                  )
                )
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
    }

    LaunchedEffect(
      key1 = replyViewMode,
      block = { popupRepliesScreenViewModel.loadRepliesForModeInitial(replyViewMode) }
    )
  }

  @Composable
  private fun BuildPopupRepliesScreenContent(
    postListOptions: PostListOptions
  ) {
    val chanTheme = LocalChanTheme.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Box(
      modifier = Modifier
        .layoutId(postListLayoutId)
    ) {
      PostListContent(
        postListOptions = postListOptions,
        postsScreenViewModel = popupRepliesScreenViewModel,
        onPostCellClicked = { postCellData ->
        },
        onPostCellLongClicked = { postCellData ->
          coroutineScope.launch { postLongtapContentMenu.showMenu(postListOptions, postCellData) }
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
        onPostImageClicked = { _, postImageData, thumbnailBoundsInRoot ->
          val collectedImages = popupRepliesScreenViewModel.collectCurrentImages()
          if (collectedImages.isEmpty()) {
            return@PostListContent
          }

          clickedThumbnailBoundsStorage.storeBounds(postImageData, thumbnailBoundsInRoot)

          val mediaViewerScreen = MediaViewerScreen(
            mediaViewerParams = MediaViewerParams.Images(
              images = collectedImages,
              initialImageUrl = postImageData.fullImageAsUrl
            ),
            openedFromScreen = screenKey,
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
        loadingContent = { isInPopup -> /*no-op*/ },
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

  override suspend fun onFloatingControllerBackPressed(): Boolean {
    if (popupRepliesScreenViewModel.popReplyChain()) {
      return true
    }

    return super.onFloatingControllerBackPressed()
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