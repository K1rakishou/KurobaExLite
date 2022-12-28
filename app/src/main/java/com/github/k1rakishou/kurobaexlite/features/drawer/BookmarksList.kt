package com.github.k1rakishou.kurobaexlite.features.drawer

import android.content.Context
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkStatsUi
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomUnitText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefreshState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.collectTextFontSize
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.ReorderableState
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.detectReorder
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.draggedItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.reorderable
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberKurobaTextUnit
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine


@Composable
fun BookmarksList(
  searchQuery: String,
  pullToRefreshState: PullToRefreshState,
  reorderableState: ReorderableState,
  showRevertBookmarkDeletion: (ThreadBookmark, Int) -> Unit
) {
  val windowInsets = LocalWindowInsets.current

  val drawerScreenViewModel: DrawerScreenViewModel = koinRememberViewModel()
  val threadScreenViewModel: ThreadScreenViewModel = koinRememberViewModel()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val contentPadding = remember(key1 = windowInsets) { PaddingValues(bottom = windowInsets.bottom) }
  val pullToRefreshToPadding = remember(key1 = contentPadding) { contentPadding.calculateTopPadding() }

  val bookmarkListBeforeFiltering = drawerScreenViewModel.bookmarksList
  val canUseFancyAnimations by drawerScreenViewModel.canUseFancyAnimations

  var bookmarkList by remember { mutableStateOf(bookmarkListBeforeFiltering) }
  var isInSearchMode by remember { mutableStateOf(false) }
  val searchQueryUpdated by rememberUpdatedState(newValue = searchQuery)

  LaunchedEffect(
    key1 = Unit,
    block = {
      combine(
        flow = snapshotFlow { bookmarkListBeforeFiltering },
        flow2 = snapshotFlow { searchQueryUpdated },
        transform = { a, b -> a to b }
      ).collectLatest { (bookmarks, query) ->
        if (query.isEmpty()) {
          Snapshot.withMutableSnapshot {
            bookmarkList = bookmarks
            isInSearchMode = false
          }

          return@collectLatest
        }

        delay(250L)

        val bookmarkListAfterFiltering = bookmarks
          .filter { threadBookmarkUi -> threadBookmarkUi.matchesQuery(query) }

        Snapshot.withMutableSnapshot {
          bookmarkList = bookmarkListAfterFiltering
          isInSearchMode = true
        }
      }
    })

  PullToRefresh(
    pullToRefreshState = pullToRefreshState,
    topPadding = pullToRefreshToPadding,
    onTriggered = { drawerScreenViewModel.forceRefreshBookmarks() }
  ) {
    LazyColumnWithFastScroller(
      lazyListContainerModifier = Modifier
        .fillMaxSize(),
      lazyListModifier = Modifier
        .fillMaxSize()
        .reorderable(
          state = reorderableState,
          canDragOver = { key, _ -> (key as? String)?.startsWith(DrawerScreen.BookmarkAnnotatedContent.bookmarkItemKey) == true },
          onMove = { from, to -> drawerScreenViewModel.onMove(from, to) },
          onDragEnd = { from, to -> drawerScreenViewModel.onMoveConfirmed(from, to) }
        ),
      lazyListState = reorderableState.lazyListState,
      contentPadding = contentPadding,
      content = {
        if (bookmarkList.isEmpty()) {
          if (isInSearchMode) {
            item(
              key = DrawerScreen.BookmarkAnnotatedContent.noBookmarksFoundMessageItemKey,
              contentType = DrawerScreen.BookmarkAnnotatedContent.noBookmarksFoundMessageItemKey,
              content = {
                KurobaComposeText(
                  modifier = Modifier
                    .fillParentMaxSize()
                    .padding(8.dp),
                  text = stringResource(id = R.string.bookmark_screen_found_by_query, searchQuery),
                  textAlign = TextAlign.Center,
                  fontSize = 16.sp
                )
              }
            )
          } else {
            item(
              key = DrawerScreen.BookmarkAnnotatedContent.noBookmarksAddedMessageItemKey,
              contentType = DrawerScreen.BookmarkAnnotatedContent.noBookmarksAddedMessageItemKey,
              content = {
                KurobaComposeText(
                  modifier = Modifier
                    .fillParentMaxSize()
                    .padding(8.dp),
                  text = stringResource(id = R.string.bookmark_screen_no_bookmarks_added),
                  textAlign = TextAlign.Center,
                  fontSize = 16.sp
                )
              }
            )
          }
        } else {
          items(
            count = bookmarkList.size,
            key = { index -> dragKey(bookmarkList[index].threadDescriptor) },
            contentType = { "bookmark_item" },
            itemContent = { index ->
              val threadBookmarkUi = bookmarkList[index]

              ThreadBookmarkItem(
                searchQuery = searchQuery,
                canUseFancyAnimations = canUseFancyAnimations,
                threadBookmarkUi = threadBookmarkUi,
                reorderableState = reorderableState,
                onBookmarkClicked = { clickedThreadBookmarkUi ->
                  threadScreenViewModel.loadThread(clickedThreadBookmarkUi.threadDescriptor)
                  globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
                  globalUiInfoManager.closeDrawer(withAnimation = true)
                },
                onBookmarkThumbnailClicked = { threadDescriptor ->
                  drawerScreenViewModel.toggleBookmarkWatchState(threadDescriptor)
                },
                onBookmarkDeleted = { clickedThreadBookmarkUi ->
                  drawerScreenViewModel.deleteBookmark(
                    threadDescriptor = clickedThreadBookmarkUi.threadDescriptor,
                    onBookmarkDeleted = { deletedBookmark, oldPosition ->
                      showRevertBookmarkDeletion(deletedBookmark, oldPosition)
                    }
                  )
                },
              )
            }
          )
        }
      }
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.ThreadBookmarkItem(
  searchQuery: String,
  canUseFancyAnimations: Boolean,
  threadBookmarkUi: ThreadBookmarkUi,
  reorderableState: ReorderableState,
  onBookmarkClicked: (ThreadBookmarkUi) -> Unit,
  onBookmarkThumbnailClicked: (ThreadDescriptor) -> Unit,
  onBookmarkDeleted: (ThreadBookmarkUi) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val defaultItemHeight = dimensionResource(id = R.dimen.history_or_bookmark_item_height)
  val animationDurationMs = 500

  val drawerScreenViewModel: DrawerScreenViewModel = koinRememberViewModel()
  val postCommentApplier: PostCommentApplier = koinRemember()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val isDrawerCurrentlyOpened by listenForDrawerVisibilityEvents()
  val globalFontSizeMultiplier by globalUiInfoManager.globalFontSizeMultiplier.collectAsState()

  val textAnimationSpec = remember(key1 = isDrawerCurrentlyOpened) {
    if (isDrawerCurrentlyOpened && canUseFancyAnimations) {
      tween<Int>(durationMillis = animationDurationMs)
    } else {
      snap<Int>()
    }
  }

  val defaultBgColor = if (drawerScreenViewModel.bookmarksToMark.containsKey(threadBookmarkUi.threadDescriptor)) {
    chanTheme.accentColor.copy(alpha = 0.3f)
  } else {
    chanTheme.backColor
  }

  val selectedOnBackColor = remember(key1 = chanTheme.selectedOnBackColor) {
    chanTheme.selectedOnBackColor.copy(alpha = 0.5f)
  }

  var threadBookmarkHash by remember { mutableStateOf(threadBookmarkUi.hashCode()) }
  val bgAnimatable = remember { Animatable(defaultBgColor) }

  LaunchedEffect(
    key1 = threadBookmarkHash,
    key2 = threadBookmarkUi.hashCode(),
    block = {
      if (threadBookmarkUi.hashCode() == threadBookmarkHash) {
        return@LaunchedEffect
      }

      try {
        bgAnimatable.animateTo(selectedOnBackColor, tween(250))
        delay(500)
        bgAnimatable.animateTo(defaultBgColor, tween(250))
      } finally {
        bgAnimatable.snapTo(defaultBgColor)
        threadBookmarkHash = threadBookmarkUi.hashCode()
      }
    }
  )

  val bookmarkBgColor by bgAnimatable.asState()
  val offset by remember(key1 = threadBookmarkUi.threadDescriptor) {
    derivedStateOf {
      reorderableState.offsetByKey(dragKey(threadBookmarkUi.threadDescriptor))
    }
  }

  val itemHeight = defaultItemHeight * (globalFontSizeMultiplier / 100f)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(itemHeight)
      .padding(vertical = 2.dp)
      .draggedItem(offset)
      .kurobaClickable(onClick = { onBookmarkClicked(threadBookmarkUi) })
      .drawBehind {
        if (reorderableState.draggedKey == dragKey(threadBookmarkUi.threadDescriptor)) {
          drawRect(bookmarkBgColor)
        }
      }
      .animateItemPlacement(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (searchQuery.isEmpty()) {
      KurobaComposeIcon(
        modifier = Modifier
          .size(DrawerScreen.BookmarkAnnotatedContent.deleteBookmarkIconWidth)
          .padding(2.dp)
          .kurobaClickable(
            bounded = false,
            onClick = { onBookmarkDeleted(threadBookmarkUi) }
          ),
        drawableId = R.drawable.ic_baseline_close_24
      )
    }

    Spacer(modifier = Modifier.width(4.dp))

    val threadDescriptor = threadBookmarkUi.threadDescriptor
    val threadBookmarkStatsUi = threadBookmarkUi.threadBookmarkStatsUi

    val watching by threadBookmarkStatsUi.watching
    val isArchived by threadBookmarkStatsUi.isArchived
    val isDeleted by threadBookmarkStatsUi.isDeleted
    val isDead = isArchived || isDeleted
    val thumbnailSize = dimensionResource(id = R.dimen.history_or_bookmark_thumbnail_size)

    val titleMut by threadBookmarkUi.title
    val title = titleMut
    val thumbnailUrlMut by threadBookmarkUi.thumbnailUrl
    val thumbnailUrl = thumbnailUrlMut

    if (thumbnailUrl != null) {
      BookmarkThumbnail(
        modifier = Modifier
          .size(thumbnailSize)
          .graphicsLayer { alpha = if (isDead) 0.5f else 1f },
        threadDescriptor = threadDescriptor,
        thumbnailUrl = thumbnailUrl,
        watching = watching,
        isDead = isDead,
        onBookmarkThumbnailClicked = onBookmarkThumbnailClicked
      )

      Spacer(modifier = Modifier.width(8.dp))
    } else {
      Shimmer(
        modifier = Modifier
          .size(thumbnailSize)
      )
    }

    Spacer(modifier = Modifier.width(4.dp))

    Column(
      modifier = Modifier.weight(1f)
    ) {
      ThreadBookmarkTitleInfo(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.5f),
        title = title,
        searchQuery = searchQuery,
        chanTheme = chanTheme,
        isDead = isDead,
        watching = watching,
        postCommentApplier = postCommentApplier
      )

      ThreadBookmarkAdditionalInfo(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.5f),
        threadDescriptor = threadBookmarkUi.threadDescriptor,
        threadBookmarkStatsUi = threadBookmarkStatsUi,
        textAnimationSpecProvider = { textAnimationSpec },
      )
    }

    if (searchQuery.isEmpty()) {
      KurobaComposeIcon(
        modifier = Modifier
          .size(32.dp)
          .padding(all = 4.dp)
          .detectReorder(reorderableState),
        drawableId = R.drawable.ic_baseline_reorder_24
      )
    }

    Spacer(modifier = Modifier.width(4.dp))
  }
}

@Composable
private fun ColumnScope.ThreadBookmarkTitleInfo(
  modifier: Modifier = Modifier,
  title: String?,
  searchQuery: String,
  chanTheme: ChanTheme,
  isDead: Boolean,
  watching: Boolean,
  postCommentApplier: PostCommentApplier
) {
  if (title != null) {
    val textFormatted = remember(searchQuery, chanTheme, isDead, watching) {
      val textColor = if (isDead) {
        chanTheme.textColorHint
      } else {
        chanTheme.textColorPrimary
      }

      return@remember buildAnnotatedString {
        val titleFormatted = buildAnnotatedString { withStyle(SpanStyle(color = textColor)) { append(title) } }

        if (searchQuery.isNotEmpty()) {
          val (_, titleFormattedWithSearchQuery) = postCommentApplier.markOrUnmarkSearchQuery(
            chanTheme = chanTheme,
            searchQuery = searchQuery,
            minQueryLength = 1,
            string = titleFormatted
          )

          append(titleFormattedWithSearchQuery)
        } else {
          append(titleFormatted)
        }
      }
    }

    KurobaComposeText(
      modifier = modifier,
      text = textFormatted,
      fontSize = 15.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  } else {
    Shimmer(
      modifier = modifier
    )
  }
}


@Composable
private fun listenForDrawerVisibilityEvents(): State<Boolean> {
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val drawerVisibility by globalUiInfoManager.drawerVisibilityFlow
    .collectAsState(initial = DrawerVisibility.Closed)

  return remember { derivedStateOf { drawerVisibility.isOpened } }
}

@Composable
private fun ColumnScope.ThreadBookmarkAdditionalInfo(
  modifier: Modifier,
  threadDescriptor: ThreadDescriptor,
  threadBookmarkStatsUi: ThreadBookmarkStatsUi,
  textAnimationSpecProvider: () -> FiniteAnimationSpec<Int>
) {
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current

  val transition = updateTransition(
    targetState = threadBookmarkStatsUi,
    label = "Bookmark animation"
  )

  val newPostsAnimated by transition.animateInt(
    label = "New posts text animation",
    transitionSpec = { textAnimationSpecProvider() },
    targetValueByState = { state -> state.newPosts.value }
  )
  val newQuotesAnimated by transition.animateInt(
    label = "New quotes text animation",
    transitionSpec = { textAnimationSpecProvider() },
    targetValueByState = { state -> state.newQuotes.value }
  )
  val totalPostsAnimated by transition.animateInt(
    label = "Total posts text animation",
    transitionSpec = { textAnimationSpecProvider() },
    targetValueByState = { state -> state.totalPosts.value }
  )

  val totalPosts by threadBookmarkStatsUi.totalPosts
  val watching by threadBookmarkStatsUi.watching
  val isFirstFetch by threadBookmarkStatsUi.isFirstFetch
  val totalPages by threadBookmarkStatsUi.totalPages
  val currentPage by threadBookmarkStatsUi.currentPage
  val isBumpLimit by threadBookmarkStatsUi.isBumpLimit
  val isImageLimit by threadBookmarkStatsUi.isImageLimit
  val isArchived by threadBookmarkStatsUi.isArchived
  val isDeleted by threadBookmarkStatsUi.isDeleted
  val isClosed by threadBookmarkStatsUi.isClosed
  val isError by threadBookmarkStatsUi.isError
  val isDead = isArchived || isDeleted

  val threadBookmarkStatsCombined by remember(
    newPostsAnimated,
    newQuotesAnimated,
    totalPostsAnimated,
    totalPosts,
    watching,
    isFirstFetch,
    totalPages,
    currentPage,
    isBumpLimit,
    isImageLimit,
    isArchived,
    isClosed,
    isDeleted,
    isError,
    isDead,
  ) {
    derivedStateOf {
      DrawerScreen.ThreadBookmarkStatsCombined(
        bookmarkedThreadHasPosts = totalPosts > 0,
        newPostsAnimated = newPostsAnimated,
        newQuotesAnimated = newQuotesAnimated,
        totalPostsAnimated = totalPostsAnimated,
        watching = watching,
        isFirstFetch = isFirstFetch,
        totalPages = totalPages,
        currentPage = currentPage,
        isBumpLimit = isBumpLimit,
        isImageLimit = isImageLimit,
        isArchived = isArchived,
        isDeleted = isDeleted,
        isClosed = isClosed,
        isError = isError,
        isDead = isDead
      )
    }
  }

  val bookmarkAdditionalInfoText = remember(chanTheme, threadBookmarkStatsCombined) {
    convertBookmarkStateToText(
      context = context,
      chanTheme = chanTheme,
      threadDescriptor = threadDescriptor,
      threadBookmarkStatsCombined = threadBookmarkStatsCombined
    )
  }

  val defaultFontSize = rememberKurobaTextUnit(fontSize = 13.sp)
  val fontSize = collectTextFontSize(defaultFontSize)

  val bookmarkInlinedContent: ImmutableMap<String, InlineTextContent> = remember(isDead, fontSize) {
    val resultMap = mutableMapOf<String, InlineTextContent>()

    DrawerScreen.BookmarkAnnotatedContent.values().forEach { bookmarkAnnotatedContent ->
      resultMap[bookmarkAnnotatedContent.id] = InlineTextContent(
        placeholder = Placeholder(fontSize, fontSize, PlaceholderVerticalAlign.Center),
        children = { DrawerScreen.BookmarkAnnotatedContent.Content(bookmarkAnnotatedContent, isDead) }
      )
    }

    return@remember resultMap.toImmutableMap()
  }

  KurobaComposeCustomUnitText(
    modifier = modifier,
    color = Color.Unspecified,
    fontSize = defaultFontSize,
    text = bookmarkAdditionalInfoText,
    inlineContent = bookmarkInlinedContent
  )
}

@Composable
private fun BookmarkThumbnail(
  modifier: Modifier = Modifier,
  threadDescriptor: ThreadDescriptor,
  thumbnailUrl: String,
  watching: Boolean,
  isDead: Boolean,
  onBookmarkThumbnailClicked: (ThreadDescriptor) -> Unit
) {
  val context = LocalContext.current

  BoxWithConstraints(
    modifier = modifier.then(
      Modifier.kurobaClickable(
        bounded = false,
        onClick = { onBookmarkThumbnailClicked(threadDescriptor) }
      )
    )
  ) {
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

    val transformations = remember(key1 = isDead, key2 = watching) {
      if (isDead) {
        listOf(
          DrawerScreen.BookmarkAnnotatedContent.circleCropTransformation,
          DrawerScreen.BookmarkAnnotatedContent.grayscaleTransformation
        )
      } else {
        listOf(DrawerScreen.BookmarkAnnotatedContent.circleCropTransformation)
      }
    }

    val imageRequest = remember(key1 = isDead, key2 = watching, key3 = thumbnailUrl) {
      ImageRequest.Builder(context)
        .data(thumbnailUrl)
        .crossfade(true)
        .transformations(transformations)
        .size(Size.ORIGINAL)
        .build()
    }

    SubcomposeAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = imageRequest,
      contentScale = ContentScale.Crop,
      contentDescription = "Bookmark thumbnail",
      content = {
        val state = painter.state

        if (state is AsyncImagePainter.State.Error) {
          logcatError(DrawerScreen.TAG) {
            "BookmarkThumbnail() url=${thumbnailUrl}, error=${state.result.throwable.errorMessageOrClassName()}"
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
  threadDescriptor: ThreadDescriptor,
  threadBookmarkStatsCombined: DrawerScreen.ThreadBookmarkStatsCombined
): AnnotatedString {
  val bookmarkedThreadHasPosts = threadBookmarkStatsCombined.bookmarkedThreadHasPosts
  val newPostsAnimated = threadBookmarkStatsCombined.newPostsAnimated
  val totalPostsAnimated = threadBookmarkStatsCombined.totalPostsAnimated
  val newQuotesAnimated = threadBookmarkStatsCombined.newQuotesAnimated
  val totalPages = threadBookmarkStatsCombined.totalPages
  val currentPage = threadBookmarkStatsCombined.currentPage
  val isBumpLimit = threadBookmarkStatsCombined.isBumpLimit
  val isImageLimit = threadBookmarkStatsCombined.isImageLimit
  val isDeleted = threadBookmarkStatsCombined.isDeleted
  val isArchived = threadBookmarkStatsCombined.isArchived
  val isClosed = threadBookmarkStatsCombined.isClosed
  val isError = threadBookmarkStatsCombined.isError
  val isDead = threadBookmarkStatsCombined.isDead
  val watching = threadBookmarkStatsCombined.watching
  val isFirstFetch = threadBookmarkStatsCombined.isFirstFetch

  val defaultTextColor = if (isDead) {
    chanTheme.textColorHint
  } else {
    chanTheme.textColorSecondary
  }

  return buildAnnotatedString {
    pushStyle(SpanStyle(color = defaultTextColor))

    append("/")
    append(threadDescriptor.catalogDescriptor.boardCode)
    append("/")

    if (isFirstFetch) {
      append(AppConstants.TEXT_SEPARATOR)
      append(context.getString(R.string.bookmark_loading_state))
      return@buildAnnotatedString
    }

    if (bookmarkedThreadHasPosts) {
      append(AppConstants.TEXT_SEPARATOR)

      append(
        buildAnnotatedString {
          append(
            buildAnnotatedString {
              if (!isDead && newPostsAnimated > 0) {
                pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
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
                if (!isDead) {
                  pushStyle(SpanStyle(color = chanTheme.bookmarkCounterHasRepliesColor))
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
    }

    if (totalPages > 0) {
      if (length > 0) {
        append(AppConstants.TEXT_SEPARATOR)
      }

      append("Pg: ")
      append(
        buildAnnotatedString {
          if (!isDead && currentPage >= totalPages) {
            pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
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
            pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
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
            pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
          }

          append("IL")
        }
      )
    }

    if (!watching && !isDead) {
      if (length > 0) {
        append(AppConstants.TEXT_SEPARATOR)
      }

      withStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor)) { append("[NW]") }
    }

    if (isDeleted) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(DrawerScreen.BookmarkAnnotatedContent.ThreadDeleted.id)
    } else if (isArchived) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(DrawerScreen.BookmarkAnnotatedContent.ThreadArchived.id)
    } else if (isClosed) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(DrawerScreen.BookmarkAnnotatedContent.ThreadClosed.id)
    }

    if (isError) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(DrawerScreen.BookmarkAnnotatedContent.ThreadError.id)
    }
  }
}

private fun dragKey(threadDescriptor: ThreadDescriptor) = "${DrawerScreen.BookmarkAnnotatedContent.bookmarkItemKey}_${threadDescriptor.asKey()}"