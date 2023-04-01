package com.github.k1rakishou.kurobaexlite.features.drawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreenViewModel
import com.github.k1rakishou.kurobaexlite.helpers.image.GrayscaleTransformation
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.DrawerContentType
import com.github.k1rakishou.kurobaexlite.helpers.util.PinHelper
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.managers.BookmarksManager
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.model.data.ui.UiNavigationElement
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomUnitText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.coerceIn
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.ScrollbarDimens
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.scrollbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val circleCropTransformation = CircleCropTransformation()
private val grayscaleTransformation = GrayscaleTransformation()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenMiniDrawerLayout() {
  val density = LocalDensity.current
  val windowInsets = LocalWindowInsets.current
  val chanTheme = LocalChanTheme.current

  val appSettings = koinRemember<AppSettings>()
  val globalUiInfoManager = koinRemember<GlobalUiInfoManager>()
  val threadScreenViewModel = koinRememberViewModel<ThreadScreenViewModel>()
  val catalogScreenViewModel = koinRememberViewModel<CatalogScreenViewModel>()
  val bookmarksScreenViewModel = koinRememberViewModel<BookmarksScreenViewModel>()
  val historyScreenViewModel = koinRememberViewModel<HistoryScreenViewModel>()

  val drawerContentTypeMut by appSettings.drawerContentType.listen().collectAsState(initial = null)
  val drawerContentType = drawerContentTypeMut

  if (drawerContentType == null) {
    return
  }

  val lazyListState = rememberLazyListState()
  var miniDrawerElements by remember { mutableStateOf<List<MiniDrawerElement>>(emptyList()) }

  LaunchedEffect(
    key1 = drawerContentType,
    block = {
      when (drawerContentType) {
        DrawerContentType.History -> {
          historyScreenViewModel.navigationHistoryListFlow
            .collect { uiNavigationElements ->
              miniDrawerElements = MiniDrawerElement.History.fromUiNavigationElements(uiNavigationElements)
            }
        }
        DrawerContentType.Bookmarks -> {
          bookmarksScreenViewModel.bookmarksListFlow
            .collect { threadBookmarkUiList ->
              miniDrawerElements = MiniDrawerElement.Bookmark.fromThreadBookmarks(threadBookmarkUiList)
            }
        }
      }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      snapshotFlow { miniDrawerElements }
        .collectLatest {
          delay(250)

          val firstCompletelyVisibleItem = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { lazyListItemInfo -> lazyListItemInfo.offset >= 0 }
            ?: return@collectLatest

          if (firstCompletelyVisibleItem.index <= 1) {
            lazyListState.animateScrollToItem(0)
          }
        }
    }
  )

  val contentPadding = remember {
    PaddingValues(
      start = 8.dp,
      end = 8.dp,
      top = windowInsets.top,
      bottom = windowInsets.bottom
    )
  }

  val scrollbarDimens = with(density) {
    remember {
      ScrollbarDimens.Vertical.Static(
        width = 4.dp.roundToPx(),
        height = 24.dp.roundToPx()
      )
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .scrollbar(
        state = lazyListState,
        scrollbarDimens = scrollbarDimens,
        scrollbarThumbColorNormal = chanTheme.scrollbarThumbColorDragged,
        contentPadding = contentPadding
      ),
    state = lazyListState,
    contentPadding = contentPadding,
    content = {
      items(
        count = miniDrawerElements.size,
        key = { index -> miniDrawerElements[index].key },
        itemContent = { index ->
          val miniDrawerElement = miniDrawerElements[index]

          Column(modifier = Modifier.animateItemPlacement()) {
            BuildMiniDrawerElement(
              miniDrawerElement = miniDrawerElement,
              onClicked = { clickedElement ->
                when (clickedElement) {
                  is MiniDrawerElement.Bookmark -> {
                    threadScreenViewModel.loadThread(clickedElement.threadDescriptor)
                    globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
                  }
                  is MiniDrawerElement.History -> {
                    when (clickedElement.chanDescriptor) {
                      is CatalogDescriptor -> {
                        catalogScreenViewModel.loadCatalog(clickedElement.chanDescriptor)
                        globalUiInfoManager.updateCurrentPage(CatalogScreen.SCREEN_KEY)
                      }
                      is ThreadDescriptor -> {
                        threadScreenViewModel.loadThread(clickedElement.chanDescriptor)
                        globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
                      }
                    }

                    historyScreenViewModel.reorderNavigationElement(clickedElement.chanDescriptor)
                  }
                }
              }
            )

            if (index < miniDrawerElements.lastIndex) {
              Spacer(modifier = Modifier.height(8.dp))
            }
          }
        }
      )
    }
  )
}

@Composable
private fun BuildMiniDrawerElement(
  miniDrawerElement: MiniDrawerElement,
  onClicked: (MiniDrawerElement) -> Unit
) {
  val context = LocalContext.current
  val bookmarksManager = koinRemember<BookmarksManager>()

  val additionalBookmarkInfo by rememberSaveable(stateSaver = AdditionalBookmarkInfo.Saver()) {
    mutableStateOf<AdditionalBookmarkInfo>(AdditionalBookmarkInfo())
  }

  val isBookmarkedThreadDead by additionalBookmarkInfo.isDead

  LaunchedEffect(
    key1 = miniDrawerElement,
    block = {
      if (miniDrawerElement !is MiniDrawerElement.Bookmark) {
        return@LaunchedEffect
      }

      suspend fun readBookmarkInfo(threadDescriptor: ThreadDescriptor) {
        val threadBookmark = bookmarksManager.getBookmark(threadDescriptor)
        if (threadBookmark == null) {
          additionalBookmarkInfo.reset()
          return
        }

        Snapshot.withMutableSnapshot {
          additionalBookmarkInfo.isDead.value = threadBookmark.isDead()
          additionalBookmarkInfo.newPosts.value = threadBookmark.newPostsCount()
          additionalBookmarkInfo.totalPosts.value = threadBookmark.totalPostsCount
          additionalBookmarkInfo.hasNewQuotes.value = threadBookmark.newQuotesCount() > 0
        }
      }

      readBookmarkInfo(miniDrawerElement.threadDescriptor)

      bookmarksManager.bookmarkEventsFlow.collect { event ->
        if (event !is BookmarksManager.Event.Updated) {
          return@collect
        }

        val eventForThisThreadBookmark = event.threadDescriptors
          .any { threadDescriptor -> threadDescriptor == miniDrawerElement.threadDescriptor }

        if (!eventForThisThreadBookmark) {
          return@collect
        }

        readBookmarkInfo(miniDrawerElement.threadDescriptor)
      }
    }
  )

  val sizeModifier = Modifier
    .fillMaxWidth()
    .aspectRatio(1f, matchHeightConstraintsFirst = false)

  val transformations = remember(key1 = isBookmarkedThreadDead) {
    if (isBookmarkedThreadDead) {
      listOf(circleCropTransformation, grayscaleTransformation)
    } else {
      listOf(circleCropTransformation)
    }
  }

  val thumbnailUrl = miniDrawerElement.thumbnailUrl
  if (thumbnailUrl != null) {
    val imageRequest = ImageRequest.Builder(context)
      .crossfade(true)
      .data(thumbnailUrl)
      .transformations(transformations)
      .build()

    Box {
      AsyncImage(
        modifier = sizeModifier.then(
          Modifier
            .kurobaClickable(bounded = false, onClick = { onClicked(miniDrawerElement) })
        ),
        model = imageRequest,
        contentScale = ContentScale.Crop,
        contentDescription = "Mini drawer icon"
      )

      if (miniDrawerElement is MiniDrawerElement.History) {
        MiniDrawerHistoryInfo(miniDrawerElement)
      } else {
        MiniDrawerBookmarkInfo(additionalBookmarkInfo)
      }
    }
  } else {
    Shimmer(modifier = sizeModifier)
  }
}

@Composable
private fun BoxScope.MiniDrawerHistoryInfo(miniDrawerHistoryElement: MiniDrawerElement.History) {
  if (!miniDrawerHistoryElement.isCatalogHistoryElement()) {
    return
  }

  val density = LocalDensity.current

  val bgColor = remember { Color.Black.copy(alpha = 0.5f) }
  val cornerRadius = with(density) {
    remember { CornerRadius(4.dp.toPx(), 4.dp.toPx()) }
  }

  val formattedText = remember {
    val chanDescriptor = miniDrawerHistoryElement.chanDescriptor
    return@remember "${chanDescriptor.siteKey.key}\n/${chanDescriptor.boardCode}/"
  }

  KurobaComposeCustomUnitText(
    modifier = Modifier
      .wrapContentSize()
      .align(Alignment.BottomCenter)
      .drawBehind { drawRoundRect(color = bgColor, cornerRadius = cornerRadius) }
      .padding(horizontal = 4.dp, vertical = 2.dp),
    text = formattedText,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    textAlign = TextAlign.Center,
    fontSize = 12.sp.coerceIn(min = 12.sp, max = 12.sp),
    color = Color.White
  )
}

@Composable
private fun BoxScope.MiniDrawerBookmarkInfo(additionalBookmarkInfo: AdditionalBookmarkInfo) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val isDead by additionalBookmarkInfo.isDead
  val hasNewQuotes by additionalBookmarkInfo.hasNewQuotes

  val transition = updateTransition(
    targetState = additionalBookmarkInfo,
    label = "Bookmark animation"
  )

  val newPostsAnimated by transition.animateInt(
    label = "New posts text animation",
    transitionSpec = { tween<Int>(durationMillis = 500) },
    targetValueByState = { state -> state.newPosts.value }
  )
  val totalPostsAnimated by transition.animateInt(
    label = "Total posts text animation",
    transitionSpec = { tween<Int>(durationMillis = 500) },
    targetValueByState = { state -> state.totalPosts.value }
  )

  val formattedText = remember(
    chanTheme,
    newPostsAnimated,
    totalPostsAnimated,
    hasNewQuotes,
    isDead
  ) {
    return@remember buildAnnotatedString {
      if (hasNewQuotes) {
        pushStyle(SpanStyle(color = chanTheme.bookmarkCounterHasRepliesColor))
      } else if (!isDead && newPostsAnimated > 0) {
        pushStyle(SpanStyle(color = chanTheme.bookmarkCounterNormalColor))
      } else {
        val defaultTextColor = if (isDead) {
          chanTheme.textColorHint
        } else {
          chanTheme.textColorPrimary
        }

        pushStyle(SpanStyle(color = defaultTextColor))
      }

      append(PinHelper.getShortUnreadCount(newPostsAnimated))
      append("/")
      append(PinHelper.getShortUnreadCount(totalPostsAnimated))
    }
  }

  val bgColor = remember { Color.Black.copy(alpha = 0.7f) }
  val cornerRadius = with(density) {
    remember { CornerRadius(4.dp.toPx(), 4.dp.toPx()) }
  }

  val alphaAnimation by animateFloatAsState(targetValue = if (additionalBookmarkInfo.canShow) 1f else 0f)

  KurobaComposeCustomUnitText(
    modifier = Modifier
      .wrapContentSize()
      .align(Alignment.BottomCenter)
      .graphicsLayer { alpha = alphaAnimation }
      .drawBehind { drawRoundRect(color = bgColor, cornerRadius = cornerRadius) }
      .padding(horizontal = 4.dp, vertical = 2.dp),
    text = formattedText,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    fontSize = 12.sp.coerceIn(min = 12.sp, max = 12.sp),
    color = Color.White
  )
}

@Stable
private class AdditionalBookmarkInfo(
  isDead: Boolean = false,
  newPosts: Int = 0,
  totalPosts: Int = 0,
  hasNewQuotes: Boolean = false,
) {
  val isDead = mutableStateOf(isDead)
  val newPosts = mutableStateOf(newPosts)
  val totalPosts = mutableStateOf(totalPosts)
  val hasNewQuotes = mutableStateOf(hasNewQuotes)

  val canShow: Boolean
    get() = totalPosts.value > 0

  fun reset() {
    Snapshot.withMutableSnapshot {
      isDead.value = false
      newPosts.value = 0
      totalPosts.value = 0
      hasNewQuotes.value = false
    }
  }

  companion object {
    fun Saver() = listSaver<AdditionalBookmarkInfo, Any>(
      save = {
        listOf(
          it.isDead.value,
          it.newPosts.value,
          it.totalPosts.value,
          it.hasNewQuotes.value
        )
      },
      restore = {
        AdditionalBookmarkInfo(
          isDead = it[0] as Boolean,
          newPosts = it[1] as Int,
          totalPosts = it[2] as Int,
          hasNewQuotes = it[3] as Boolean,
        )
      }
    )
  }
}

@Immutable
private sealed class MiniDrawerElement {
  abstract val thumbnailUrl: String?

  fun isCatalogHistoryElement(): Boolean {
    return this is History && chanDescriptor is CatalogDescriptor
  }

  val key: String
    get() {
      val prefix = when (this) {
        is Bookmark -> "bookmark"
        is History -> "history"
      }

      val key = when (this) {
        is Bookmark -> threadDescriptor.asKey()
        is History -> chanDescriptor.asKey()
      }

      return "${prefix}_${key}"
    }

  data class Bookmark(
    val threadDescriptor: ThreadDescriptor,
    override val thumbnailUrl: String?
  ) : MiniDrawerElement() {

    companion object {
      fun fromThreadBookmarks(threadBookmarkUiList: List<ThreadBookmarkUi>): List<MiniDrawerElement> {
        return threadBookmarkUiList.map { threadBookmarkUi ->
          return@map Bookmark(
            threadDescriptor = threadBookmarkUi.threadDescriptor,
            thumbnailUrl = threadBookmarkUi.thumbnailUrl.value
          )
        }
      }
    }

  }

  data class History(
    val chanDescriptor: ChanDescriptor,
    override val thumbnailUrl: String?
  ) : MiniDrawerElement() {

    companion object {
      fun fromUiNavigationElements(uiNavigationElements: List<UiNavigationElement>): List<MiniDrawerElement> {
        return uiNavigationElements.map { uiNavigationElement ->
          return@map History(
            chanDescriptor = uiNavigationElement.chanDescriptor,
            thumbnailUrl = uiNavigationElement.iconUrl
          )
        }
      }

    }
  }

}