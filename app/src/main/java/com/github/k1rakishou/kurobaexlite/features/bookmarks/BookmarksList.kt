package com.github.k1rakishou.kurobaexlite.features.bookmarks

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.TextFieldValue
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
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefresh
import com.github.k1rakishou.kurobaexlite.ui.helpers.PullToRefreshState
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.ReorderableState
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.detectReorder
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.draggedItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.reorder.reorderable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine


@Composable
fun BookmarksList(
  pullToRefreshState: PullToRefreshState,
  reorderableState: ReorderableState,
  showRevertBookmarkDeletion: (ThreadBookmark, Int) -> Unit
) {
  val context = LocalContext.current
  val windowInsets = LocalWindowInsets.current

  val bookmarksScreenViewModel: BookmarksScreenViewModel = koinRememberViewModel()
  val threadScreenViewModel: ThreadScreenViewModel = koinRememberViewModel()
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val contentPadding = remember(key1 = windowInsets) { PaddingValues(bottom = windowInsets.bottom) }
  val pullToRefreshToPadding = remember(key1 = contentPadding) { contentPadding.calculateTopPadding() }

  val bookmarkListBeforeFiltering = bookmarksScreenViewModel.bookmarksList
  val canUseFancyAnimations by bookmarksScreenViewModel.canUseFancyAnimations

  var searchQuery by rememberSaveable(
    key = "bookmarks_search_query",
    stateSaver = TextFieldValue.Saver
  ) { mutableStateOf<TextFieldValue>(TextFieldValue()) }

  var bookmarkList by remember { mutableStateOf(bookmarkListBeforeFiltering) }
  var isInSearchMode by remember { mutableStateOf(false) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      combine(
        flow = snapshotFlow { bookmarkListBeforeFiltering },
        flow2 = snapshotFlow { searchQuery },
        transform = { a, b -> a to b }
      ).collectLatest { (bookmarks, query) ->
        if (query.text.isEmpty()) {
          Snapshot.withMutableSnapshot {
            bookmarkList = bookmarks
            isInSearchMode = false
          }

          return@collectLatest
        }

        delay(250L)

        val bookmarkListAfterFiltering = bookmarks
          .filter { threadBookmarkUi -> threadBookmarkUi.matchesQuery(query.text) }

        Snapshot.withMutableSnapshot {
          bookmarkList = bookmarkListAfterFiltering
          isInSearchMode = true
        }
      }
    })

  PullToRefresh(
    pullToRefreshState = pullToRefreshState,
    topPadding = pullToRefreshToPadding,
    onTriggered = { bookmarksScreenViewModel.forceRefreshBookmarks(context) }
  ) {
    LazyColumnWithFastScroller(
      lazyListContainerModifier = Modifier
        .fillMaxSize(),
      lazyListModifier = Modifier
        .fillMaxSize()
        .reorderable(
          state = reorderableState,
          canDragOver = { key, _ -> (key as? String)?.startsWith(BookmarksScreen.BookmarkAnnotatedContent.bookmarkItemKey) == true },
          // "idx - 1" because we have the SearchInput as the first item of the list
          onMove = { from, to -> bookmarksScreenViewModel.onMove(from - 1, to - 1) },
          onDragEnd = { from, to -> bookmarksScreenViewModel.onMoveConfirmed(from - 1, to - 1) }
        ),
      lazyListState = reorderableState.lazyListState,
      contentPadding = contentPadding,
      content = {
        if (bookmarkList.isEmpty() && !isInSearchMode) {
          item(
            key = BookmarksScreen.BookmarkAnnotatedContent.noBookmarksAddedMessageItemKey,
            contentType = BookmarksScreen.BookmarkAnnotatedContent.noBookmarksAddedMessageItemKey,
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
        } else {
          item(
            key = BookmarksScreen.BookmarkAnnotatedContent.searchInputItemKey,
            contentType = "search_input_item",
            content = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                DrawerSearchInput(
                  modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                  searchQuery = searchQuery,
                  searchingBookmarks = true,
                  onSearchQueryChanged = { query -> searchQuery = query },
                  onClearSearchQueryClicked = { searchQuery = TextFieldValue() }
                )

                Spacer(modifier = Modifier.width(8.dp))

                DrawerContentTypeToggleIcon()

                Spacer(modifier = Modifier.width(8.dp))
              }
            }
          )

          if (bookmarkList.isEmpty() && isInSearchMode) {
            item(
              key = BookmarksScreen.BookmarkAnnotatedContent.noBookmarksFoundMessageItemKey,
              contentType = BookmarksScreen.BookmarkAnnotatedContent.noBookmarksFoundMessageItemKey,
              content = {
                KurobaComposeText(
                  modifier = Modifier
                    .fillParentMaxSize()
                    .padding(8.dp),
                  text = stringResource(id = R.string.bookmark_screen_found_by_query, searchQuery.text),
                  textAlign = TextAlign.Center,
                  fontSize = 16.sp
                )
              }
            )
          } else {
            items(
              count = bookmarkList.size,
              key = { index -> dragKey(bookmarkList[index].threadDescriptor) },
              contentType = { "bookmark_item" },
              itemContent = { index ->
                val threadBookmarkUi = bookmarkList[index]

                ThreadBookmarkItem(
                  searchQuery = searchQuery.text,
                  canUseFancyAnimations = canUseFancyAnimations,
                  threadBookmarkUi = threadBookmarkUi,
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
                        showRevertBookmarkDeletion(deletedBookmark, oldPosition)
                      }
                    )
                  },
                )
              }
            )
          }
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
  onBookmarkDeleted: (ThreadBookmarkUi) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val itemHeight = dimensionResource(id = R.dimen.history_or_bookmark_item_height)
  val animationDurationMs = 500

  val bookmarksScreenViewModel: BookmarksScreenViewModel = koinRememberViewModel()
  val postCommentApplier: PostCommentApplier = koinRemember()

  val isDrawerCurrentlyOpened by listenForDrawerVisibilityEvents()

  val textAnimationSpec = remember(key1 = isDrawerCurrentlyOpened) {
    if (isDrawerCurrentlyOpened && canUseFancyAnimations) {
      tween<Int>(durationMillis = animationDurationMs)
    } else {
      snap<Int>()
    }
  }

  val defaultBgColor = if (bookmarksScreenViewModel.bookmarksToMark.containsKey(threadBookmarkUi.threadDescriptor)) {
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

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp)
      .height(itemHeight)
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
          .size(BookmarksScreen.BookmarkAnnotatedContent.deleteBookmarkIconWidth)
          .padding(2.dp)
          .kurobaClickable(
            bounded = false,
            onClick = { onBookmarkDeleted(threadBookmarkUi) }
          ),
        drawableId = R.drawable.ic_baseline_close_24
      )
    }

    Spacer(modifier = Modifier.width(4.dp))

    val threadBookmarkStatsUi = threadBookmarkUi.threadBookmarkStatsUi
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
        iconUrl = thumbnailUrl,
        isDead = isDead
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
      if (title != null) {
        val textFormatted = remember(key1 = searchQuery, key2 = chanTheme, key3 = isDead) {
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
                minQueryLength = 2,
                string = titleFormatted
              )

              append(titleFormattedWithSearchQuery)
            } else {
              append(titleFormatted)
            }
          }
        }

        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .weight(0.5f),
          text = textFormatted,
          fontSize = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      } else {
        Shimmer(
          modifier = Modifier
            .fillMaxWidth()
            .weight(0.5f)
        )
      }

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
private fun listenForDrawerVisibilityEvents(): State<Boolean> {
  val globalUiInfoManager: GlobalUiInfoManager = koinRemember()

  val drawerVisibility by globalUiInfoManager.drawerVisibilityFlow
    .collectAsState(initial = DrawerVisibility.Closed)

  return remember { derivedStateOf { drawerVisibility.isOpened } }
}

@Composable
private fun ThreadBookmarkAdditionalInfo(
  modifier: Modifier,
  threadDescriptor: ThreadDescriptor,
  threadBookmarkStatsUi: ThreadBookmarkStatsUi,
  textAnimationSpecProvider: () -> FiniteAnimationSpec<Int>
) {
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current
  val fontSize = 13.sp

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
    label = "Total posts text textAnimationSpec",
    transitionSpec = { textAnimationSpecProvider() },
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

  val threadBookmarkStatsCombined by remember(
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
    isDead,
  ) {
    derivedStateOf {
      BookmarksScreen.ThreadBookmarkStatsCombined(
        newPostsAnimated = newPostsAnimated,
        newQuotesAnimated = newQuotesAnimated,
        totalPostsAnimated = totalPostsAnimated,
        isFirstFetch = isFirstFetch,
        totalPages = totalPages,
        currentPage = currentPage,
        isBumpLimit = isBumpLimit,
        isImageLimit = isImageLimit,
        isArchived = isArchived,
        isDeleted = isDeleted,
        isError = isError,
        isDead = isDead
      )
    }
  }

  val bookmarkAdditionalInfoText = remember(threadBookmarkStatsCombined) {
    convertBookmarkStateToText(
      context = context,
      chanTheme = chanTheme,
      threadDescriptor = threadDescriptor,
      threadBookmarkStatsCombined = threadBookmarkStatsCombined
    )
  }

  val bookmarkInlinedContent = remember(key1 = isDead) {
    val resultMap = mutableMapOf<String, InlineTextContent>()

    BookmarksScreen.BookmarkAnnotatedContent.values().forEach { bookmarkAnnotatedContent ->
      resultMap[bookmarkAnnotatedContent.id] = InlineTextContent(
        placeholder = Placeholder(fontSize, fontSize, PlaceholderVerticalAlign.Center),
        children = { BookmarksScreen.BookmarkAnnotatedContent.Content(bookmarkAnnotatedContent, isDead) }
      )
    }

    return@remember resultMap
  }

  KurobaComposeText(
    modifier = modifier,
    color = Color.Unspecified,
    fontSize = fontSize,
    text = bookmarkAdditionalInfoText,
    inlineContent = bookmarkInlinedContent
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
        listOf(BookmarksScreen.BookmarkAnnotatedContent.circleCropTransformation, BookmarksScreen.BookmarkAnnotatedContent.grayscaleTransformation)
      } else {
        listOf(BookmarksScreen.BookmarkAnnotatedContent.circleCropTransformation)
      }
    }

    SubcomposeAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(context)
        .data(iconUrl)
        .crossfade(true)
        .transformations(transformations)
        .size(Size.ORIGINAL)
        .build(),
      contentScale = ContentScale.Crop,
      contentDescription = null,
      content = {
        val state = painter.state

        if (state is AsyncImagePainter.State.Error) {
          logcatError(BookmarksScreen.TAG) {
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
  threadDescriptor: ThreadDescriptor,
  threadBookmarkStatsCombined: BookmarksScreen.ThreadBookmarkStatsCombined
): AnnotatedString {
  val newPostsAnimated = threadBookmarkStatsCombined.newPostsAnimated
  val totalPostsAnimated = threadBookmarkStatsCombined.totalPostsAnimated
  val newQuotesAnimated = threadBookmarkStatsCombined.newQuotesAnimated
  val totalPages = threadBookmarkStatsCombined.totalPages
  val currentPage = threadBookmarkStatsCombined.currentPage
  val isBumpLimit = threadBookmarkStatsCombined.isBumpLimit
  val isImageLimit = threadBookmarkStatsCombined.isImageLimit
  val isDeleted = threadBookmarkStatsCombined.isDeleted
  val isArchived = threadBookmarkStatsCombined.isArchived
  val isError = threadBookmarkStatsCombined.isError
  val isDead = threadBookmarkStatsCombined.isDead
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
    append(AppConstants.TEXT_SEPARATOR)

    if (isFirstFetch) {
      append(context.getString(R.string.bookmark_loading_state))
      return@buildAnnotatedString
    }

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
              if (!isDead && newQuotesAnimated > 0) {
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

    if (isDeleted) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(BookmarksScreen.BookmarkAnnotatedContent.ThreadDeleted.id)
    } else if (isArchived) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(BookmarksScreen.BookmarkAnnotatedContent.ThreadArchived.id)
    }

    if (isError) {
      if (length > 0) {
        append(" ")
      }

      appendInlineContent(BookmarksScreen.BookmarkAnnotatedContent.ThreadError.id)
    }
  }
}

private fun dragKey(threadDescriptor: ThreadDescriptor) = "${BookmarksScreen.BookmarkAnnotatedContent.bookmarkItemKey}_${threadDescriptor.asKey()}"