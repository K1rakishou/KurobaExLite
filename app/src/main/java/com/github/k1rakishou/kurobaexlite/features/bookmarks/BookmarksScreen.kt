package com.github.k1rakishou.kurobaexlite.features.bookmarks

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.image.GrayscaleTransformation
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkStatsUi
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.ReorderableState
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.detectReorder
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.draggedItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.rememberReorderState
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.reorderable
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookmarksScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : ComposeScreen(componentActivity, navigationRouter) {
  private val bookmarksScreenViewModel: BookmarksScreenViewModel by componentActivity.viewModel()
  private val threadScreenViewModel: ThreadScreenViewModel by componentActivity.viewModel()

  private val circleCropTransformation = CircleCropTransformation()
  private val grayscaleTransformation = GrayscaleTransformation()

  override val screenKey: ScreenKey = SCREEN_KEY

  @Composable
  override fun Content() {
    val chanTheme = LocalChanTheme.current
    val windowInsets = LocalWindowInsets.current
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()

    val contentPadding = remember(key1 = windowInsets) {
      PaddingValues(top = windowInsets.top, bottom = windowInsets.bottom)
    }

    val bookmarkList = bookmarksScreenViewModel.bookmarksList
    val canUseFancyAnimations by bookmarksScreenViewModel.canUseFancyAnimations

    LaunchedEffect(
      key1 = Unit,
      block = {
        snackbarManager.snackbarElementsClickFlow.collectLatest { snackbarClickable ->
          if (snackbarClickable.key !is SnackbarButton) {
            return@collectLatest
          }

          when (snackbarClickable.key as SnackbarButton) {
            SnackbarButton.UndoThreadBookmarkDeletion -> {
              val pair = snackbarClickable.data as? Pair<Int, ThreadBookmark>
                ?: return@collectLatest

              val prevPosition = pair.first
              val threadBookmark = pair.second

              bookmarksScreenViewModel.undoBookmarkDeletion(threadBookmark, prevPosition)
            }
          }
        }
      })

    val reorderableState = rememberReorderState(lazyListState = lazyListState)

    BoxWithConstraints {
      val availableWidth = constraints.maxWidth.toFloat()

      if (availableWidth > 0) {
        LazyColumnWithFastScroller(
          lazyListContainerModifier = Modifier
            .fillMaxSize()
            .background(chanTheme.backColorCompose),
          lazyListModifier = Modifier
            .fillMaxSize()
            .reorderable(
              state = reorderableState,
              onMove = { from, to -> bookmarksScreenViewModel.onMove(from, to) },
              onDragEnd = { from, to -> bookmarksScreenViewModel.onMoveConfirmed(from, to) }
            ),
          lazyListState = reorderableState.lazyListState,
          contentPadding = contentPadding,
          content = {
            items(
              count = bookmarkList.size,
              key = { index -> bookmarkList[index].threadDescriptor },
              itemContent = { index ->
                val threadBookmarkUi = bookmarkList[index]

                ThreadBookmarkItem(
                  canUseFancyAnimations = canUseFancyAnimations,
                  threadBookmarkUi = threadBookmarkUi,
                  availableWidth = availableWidth,
                  reorderableState = reorderableState,
                  onBookmarkClicked = { clickedThreadBookmarkUi ->
                    threadScreenViewModel.loadThread(clickedThreadBookmarkUi.threadDescriptor)
                    globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
                    globalUiInfoManager.closeDrawer(withAnimation = true)
                  },
                  onBookmarkDeleted = { clickedThreadBookmarkUi ->
                    bookmarksScreenViewModel.deleteBookmark(
                      threadDescriptor = clickedThreadBookmarkUi.threadDescriptor,
                      onBookmarkDeleted = { deletedBookmark, oldPosition ->
                        showRevertBookmarkDeletion(
                          context = context,
                          deletedBookmark = deletedBookmark,
                          oldPosition = oldPosition
                        )
                      }
                    )
                  },
                )
              }
            )
          }
        )
      }
    }
  }

  private fun showRevertBookmarkDeletion(
    context: Context,
    deletedBookmark: ThreadBookmark,
    oldPosition: Int
  ) {
    val title = deletedBookmark.title ?: deletedBookmark.threadDescriptor.asReadableString()

    snackbarManager.pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.ThreadBookmarkRemoved,
        aliveUntil = SnackbarInfo.snackbarDuration(AppConstants.deleteBookmarkTimeoutMs.milliseconds),
        content = listOf(
          SnackbarContentItem.Text(
            context.getString(R.string.thread_bookmarks_screen_removed_bookmark_item_text, title)
          ),
          SnackbarContentItem.Spacer(space = 8.dp),
          SnackbarContentItem.Button(
            key = SnackbarButton.UndoThreadBookmarkDeletion,
            text = context.getString(R.string.undo),
            data = Pair(oldPosition, deletedBookmark)
          ),
          SnackbarContentItem.Spacer(space = 8.dp),
        )
      )
    )
  }

  @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
  @Composable
  private fun LazyItemScope.ThreadBookmarkItem(
    canUseFancyAnimations: Boolean,
    threadBookmarkUi: ThreadBookmarkUi,
    availableWidth: Float,
    reorderableState: ReorderableState,
    onBookmarkClicked: (ThreadBookmarkUi) -> Unit,
    onBookmarkDeleted: (ThreadBookmarkUi) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val itemHeight = dimensionResource(id = R.dimen.history_or_bookmark_item_height)
    val animationDurationMs = 500

    val drawerVisibility by globalUiInfoManager.drawerVisibilityFlow
      .collectAsState(initial = DrawerVisibility.Closed)
    val isDrawerCurrentlyOpened = drawerVisibility.isOpened

    val textAnimationSpec = remember(key1 = isDrawerCurrentlyOpened) {
      if (isDrawerCurrentlyOpened && canUseFancyAnimations) {
        tween<Int>(durationMillis = animationDurationMs)
      } else {
        snap<Int>()
      }
    }

    val selectedOnBackColorCompose = remember(key1 = chanTheme.selectedOnBackColorCompose) {
      chanTheme.selectedOnBackColorCompose.copy(alpha = 0.5f)
    }

    var threadBookmarkHash by remember { mutableStateOf(threadBookmarkUi.hashCode()) }
    val bgAnimatable = remember { Animatable(chanTheme.backColorCompose) }

    LaunchedEffect(
      key1 = threadBookmarkHash,
      key2 = threadBookmarkUi.hashCode(),
      block = {
        if (threadBookmarkUi.hashCode() == threadBookmarkHash) {
          return@LaunchedEffect
        }

        try {
          bgAnimatable.animateTo(selectedOnBackColorCompose, tween(250))
          delay(500)
          bgAnimatable.animateTo(chanTheme.backColorCompose, tween(250))
        } finally {
          bgAnimatable.snapTo(chanTheme.backColorCompose)
          threadBookmarkHash = threadBookmarkUi.hashCode()
        }
      }
    )

    val bookmarkBgColor by bgAnimatable.asState()

    val swipeableState = rememberSwipeableState(initialValue = BookmarkSwipeState.Normal)
    val anchors = remember(key1 = availableWidth) {
      mapOf(
        0f to BookmarkSwipeState.Normal,
        availableWidth to BookmarkSwipeState.Deleted
      )
    }
    val bookmarkOffset by swipeableState.offset
    val bookmarkAlpha = 1f - (bookmarkOffset / availableWidth).coerceIn(0f, 1f)
    val swipeableStateEnabled = reorderableState.draggedIndex == null

    LaunchedEffect(
      key1 = swipeableState.currentValue,
      block = {
        if (swipeableState.currentValue == BookmarkSwipeState.Deleted) {
          onBookmarkDeleted(threadBookmarkUi)

          // Reset state back, otherwise if the user undoes the deletion the state will be
          // restored the Delete again which will automatically trigger deletion again.
          swipeableState.snapTo(BookmarkSwipeState.Normal)
        }
      }
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
        .height(itemHeight)
        .draggedItem(reorderableState.offsetByKey(threadBookmarkUi.threadDescriptor))
        .kurobaClickable(onClick = { onBookmarkClicked(threadBookmarkUi) })
        .absoluteOffset { IntOffset(x = bookmarkOffset.toInt(), y = 0) }
        .background(bookmarkBgColor)
        .animateItemPlacement()
        .swipeable(
          enabled = swipeableStateEnabled,
          state = swipeableState,
          anchors = anchors,
          orientation = Orientation.Horizontal,
          thresholds = { _, _ -> FractionalThreshold(.65f) }
        )
        .graphicsLayer { alpha = bookmarkAlpha },
      verticalAlignment = Alignment.CenterVertically
    ) {
      Spacer(modifier = Modifier.width(4.dp))

      val threadBookmarkStatsUi = threadBookmarkUi.threadBookmarkStatsUi
      val isArchived by threadBookmarkStatsUi.isArchived
      val isDeleted by threadBookmarkStatsUi.isDeleted
      val isDead = isArchived || isDeleted

      if (threadBookmarkUi.thumbnailUrl != null) {
        val thumbnailSize = dimensionResource(id = R.dimen.history_or_bookmark_thumbnail_size)

        BookmarkThumbnail(
          modifier = Modifier
            .size(thumbnailSize)
            .graphicsLayer { alpha = if (isDead) 0.5f else 1f },
          iconUrl = threadBookmarkUi.thumbnailUrl,
          isDead = isDead
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      Column(modifier = Modifier.weight(1f)) {
        val textColor = if (isDead) {
          chanTheme.textColorHintCompose
        } else {
          chanTheme.textColorPrimaryCompose
        }

        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .weight(0.6f),
          text = threadBookmarkUi.title,
          color = textColor,
          fontSize = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        ThreadBookmarkAdditionalInfo(
          modifier = Modifier
            .fillMaxWidth()
            .weight(0.4f),
          threadBookmarkStatsUi = threadBookmarkStatsUi,
          textAnimationSpec = textAnimationSpec,
        )
      }

      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .padding(all = 4.dp)
          .detectReorder(reorderableState),
        drawableId = R.drawable.ic_baseline_reorder_24
      )

      Spacer(modifier = Modifier.width(4.dp))
    }
  }

  @Composable
  private fun ThreadBookmarkAdditionalInfo(
    modifier: Modifier,
    threadBookmarkStatsUi: ThreadBookmarkStatsUi,
    textAnimationSpec: FiniteAnimationSpec<Int>
  ) {
    val context = LocalContext.current
    val chanTheme = LocalChanTheme.current

    val transition = updateTransition(
      targetState = threadBookmarkStatsUi,
      label = "Bookmark animation"
    )

    val newPostsAnimated by transition.animateInt(
      label = "New posts text animation",
      transitionSpec = { textAnimationSpec },
      targetValueByState = { state -> state.newPosts.value }
    )
    val newQuotesAnimated by transition.animateInt(
      label = "New quotes text animation",
      transitionSpec = { textAnimationSpec },
      targetValueByState = { state -> state.newQuotes.value }
    )
    val totalPostsAnimated by transition.animateInt(
      label = "Total posts text textAnimationSpec",
      transitionSpec = { textAnimationSpec },
      targetValueByState = { state -> state.totalPosts.value }
    )

    val isFirstFetch by threadBookmarkStatsUi.isFirstFetch
    val totalPages by threadBookmarkStatsUi.totalPages
    val currentPage by threadBookmarkStatsUi.currentPage
    val isBumpLimit by threadBookmarkStatsUi.isBumpLimit
    val isImageLimit by threadBookmarkStatsUi.isImageLimit
    val isArchived by threadBookmarkStatsUi.isArchived
    val isDeleted by threadBookmarkStatsUi.isDeleted
    val isError by threadBookmarkStatsUi.isError

    val isDead = isArchived || isDeleted

    val bookmarkAdditionalInfoText = remember(
      newPostsAnimated,
      newQuotesAnimated,
      totalPostsAnimated,
      isFirstFetch,
      totalPages,
      currentPage,
      isBumpLimit,
      isImageLimit,
      isArchived,
      isDeleted,
      isError,
    ) {
      convertBookmarkStateToText(
        context = context,
        chanTheme = chanTheme,
        newPostsAnimated = newPostsAnimated,
        totalPostsAnimated = totalPostsAnimated,
        newQuotesAnimated = newQuotesAnimated,
        totalPages = totalPages,
        currentPage = currentPage,
        isBumpLimit = isBumpLimit,
        isImageLimit = isImageLimit,
        isDeleted = isDeleted,
        isArchived = isArchived,
        isError = isError,
        isDead = isDead,
        isFirstFetch = isFirstFetch,
      )
    }

    KurobaComposeText(
      modifier = modifier,
      color = Color.Unspecified,
      fontSize = 13.sp,
      text = bookmarkAdditionalInfoText
    )
  }

  @Composable
  private fun BookmarkThumbnail(
    modifier: Modifier = Modifier,
    iconUrl: String,
    isDead: Boolean
  ) {
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier) {
      val density = LocalDensity.current
      val desiredSizePx = with(density) { remember { 24.dp.roundToPx() } }

      val iconHeightDp = with(density) {
        remember(key1 = constraints.maxHeight) {
          desiredSizePx.coerceAtMost(constraints.maxHeight).toDp()
        }
      }
      val iconWidthDp = with(density) {
        remember(key1 = constraints.maxWidth) {
          desiredSizePx.coerceAtMost(constraints.maxWidth).toDp()
        }
      }

      val transformations = remember(key1 = isDead) {
        if (isDead) {
          listOf(circleCropTransformation, grayscaleTransformation)
        } else {
          listOf(circleCropTransformation)
        }
      }

      SubcomposeAsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = ImageRequest.Builder(context)
          .data(iconUrl)
          .crossfade(true)
          .transformations(transformations)
          .build(),
        contentScale = ContentScale.Crop,
        contentDescription = null,
        content = {
          val state = painter.state

          if (state is AsyncImagePainter.State.Error) {
            logcatError(TAG) {
              "BookmarkThumbnail() url=${iconUrl}, error=${state.result.throwable.errorMessageOrClassName()}"
            }

            KurobaComposeIcon(
              modifier = Modifier
                .size(iconWidthDp, iconHeightDp)
                .align(Alignment.Center),
              drawableId = R.drawable.ic_baseline_warning_24
            )

            return@SubcomposeAsyncImage
          }

          SubcomposeAsyncImageContent()
        }
      )
    }
  }

  private fun convertBookmarkStateToText(
    context: Context,
    chanTheme: ChanTheme,
    newPostsAnimated: Int,
    totalPostsAnimated: Int,
    newQuotesAnimated: Int,
    totalPages: Int,
    currentPage: Int,
    isBumpLimit: Boolean,
    isImageLimit: Boolean,
    isDeleted: Boolean,
    isArchived: Boolean,
    isError: Boolean,
    isDead: Boolean,
    isFirstFetch: Boolean
  ): AnnotatedString {
    val defaultTextColor = if (isDead) {
      chanTheme.textColorHintCompose
    } else {
      chanTheme.textColorSecondaryCompose
    }

    return buildAnnotatedString {
      pushStyle(SpanStyle(color = defaultTextColor))

      if (isFirstFetch) {
        append(context.getString(R.string.bookmark_loading_state))
        return@buildAnnotatedString
      }

      append(
        buildAnnotatedString {
          append(
            buildAnnotatedString {
              if (!isDead && newPostsAnimated > 0) {
                pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColorCompose))
              } else {
                pushStyle(SpanStyle(color = defaultTextColor))
              }

              append(newPostsAnimated.toString())
              append("/")
              append(totalPostsAnimated.toString())
            }
          )

          if (newQuotesAnimated > 0) {
            append(" (")
            append(
              buildAnnotatedString {
                if (!isDead && newQuotesAnimated > 0) {
                  pushStyle(SpanStyle(color = chanTheme.bookmarkCounterHasRepliesColorCompose))
                } else {
                  pushStyle(SpanStyle(color = defaultTextColor))
                }

                append(newQuotesAnimated.toString())
              }
            )
            append(")")
          }
        }
      )

      if (totalPages > 0) {
        if (length > 0) {
          append(AppConstants.TEXT_SEPARATOR)
        }

        append("Pg: ")
        append(
          buildAnnotatedString {
            if (!isDead && currentPage >= totalPages) {
              pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColorCompose))
            } else {
              pushStyle(SpanStyle(color = defaultTextColor))
            }

            append(currentPage.toString())
            append("/")
            append(totalPages.toString())
          }
        )
      }

      if (isBumpLimit) {
        if (length > 0) {
          append(AppConstants.TEXT_SEPARATOR)
        }

        append(
          buildAnnotatedString {
            if (!isDead) {
              pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColorCompose))
            }

            append("BL")
          }
        )
      }

      if (isImageLimit) {
        if (length > 0) {
          append(AppConstants.TEXT_SEPARATOR)
        }

        append(
          buildAnnotatedString {
            if (!isDead) {
              pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColorCompose))
            }

            append("IL")
          }
        )
      }

      if (isDeleted) {
        if (length > 0) {
          append(AppConstants.TEXT_SEPARATOR)
        }

        append(
          buildAnnotatedString {
            pushStyle(SpanStyle(color = chanTheme.accentColorCompose))
            append("Del")
          }
        )
      } else {
        if (isArchived) {
          if (length > 0) {
            append(AppConstants.TEXT_SEPARATOR)
          }

          append("Arch")
        }
      }

      if (isError) {
        if (length > 0) {
          append(AppConstants.TEXT_SEPARATOR)
        }

        append(
          buildAnnotatedString {
            pushStyle(SpanStyle(color = chanTheme.accentColorCompose))
            append("Err")
          }
        )
      }
    }
  }

  enum class BookmarkSwipeState {
    Normal,
    Deleted
  }

  enum class SnackbarButton {
    UndoThreadBookmarkDeletion
  }

  companion object {
    private const val TAG = "BookmarksScreen"
    val SCREEN_KEY = ScreenKey("BookmarksScreen")
  }
}