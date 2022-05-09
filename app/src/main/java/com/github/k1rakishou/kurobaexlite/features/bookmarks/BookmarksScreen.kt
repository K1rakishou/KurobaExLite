package com.github.k1rakishou.kurobaexlite.features.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
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
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkStatsUi
import com.github.k1rakishou.kurobaexlite.model.data.ui.bookmarks.ThreadBookmarkUi
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyColumnWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import logcat.logcat
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
    val lastListState = rememberLazyListState()

    val contentPadding = remember(key1 = windowInsets) {
      PaddingValues(top = windowInsets.top, bottom = windowInsets.bottom)
    }

    val bookmarkList = bookmarksScreenViewModel.bookmarksList
    val canUseFancyAnimations by bookmarksScreenViewModel.canUseFancyAnimations

    LazyColumnWithFastScroller(
      modifier = Modifier
        .fillMaxSize()
        .background(chanTheme.backColorCompose),
      lazyListState = lastListState,
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
              onBookmarkClicked = { clickedThreadBookmarkUi ->
                threadScreenViewModel.loadThread(clickedThreadBookmarkUi.threadDescriptor)
                globalUiInfoManager.updateCurrentPage(ThreadScreen.SCREEN_KEY)
                globalUiInfoManager.closeDrawer(withAnimation = true)
              },
              onDeleteBookmarkClicked = { clickedThreadBookmarkUi ->
                logcat(TAG) { "onDeleteBookmarkClicked() threadBookmarkUi=${clickedThreadBookmarkUi}" }
              },
            )
          }
        )
      }
    )
  }

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  private fun LazyItemScope.ThreadBookmarkItem(
    canUseFancyAnimations: Boolean,
    threadBookmarkUi: ThreadBookmarkUi,
    onBookmarkClicked: (ThreadBookmarkUi) -> Unit,
    onDeleteBookmarkClicked: (ThreadBookmarkUi) -> Unit
  ) {
    val chanTheme = LocalChanTheme.current
    val itemHeight = dimensionResource(id = R.dimen.history_or_bookmark_item_height)
    val drawerVisibility by globalUiInfoManager.drawerVisibilityFlow.collectAsState(initial = DrawerVisibility.Closed)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(itemHeight)
        .padding(vertical = 2.dp)
        .kurobaClickable(onClick = { onBookmarkClicked(threadBookmarkUi) })
        .animateItemPlacement(),
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

      Column(modifier = Modifier.fillMaxHeight()) {
        val textColor = if (isDead) {
          chanTheme.textColorHintCompose
        } else {
          chanTheme.textColorPrimaryCompose
        }

        KurobaComposeText(
          modifier = Modifier
            .fillMaxWidth()
            .weight(0.5f),
          text = threadBookmarkUi.title,
          color = textColor,
          fontSize = 14.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        ThreadBookmarkAdditionalInfo(
          modifier = Modifier
            .fillMaxWidth()
            .weight(0.5f),
          threadBookmarkStatsUi = threadBookmarkStatsUi,
          isDrawerCurrentlyOpened = drawerVisibility.isOpened,
          canUseFancyAnimations = canUseFancyAnimations
        )
      }
    }
  }

  @Composable
  private fun ThreadBookmarkAdditionalInfo(
    modifier: Modifier,
    threadBookmarkStatsUi: ThreadBookmarkStatsUi,
    isDrawerCurrentlyOpened: Boolean,
    canUseFancyAnimations: Boolean
  ) {
    val context = LocalContext.current
    val chanTheme = LocalChanTheme.current

    val newPosts by threadBookmarkStatsUi.newPosts
    val newQuotes by threadBookmarkStatsUi.newQuotes
    val totalPosts by threadBookmarkStatsUi.totalPosts

    val animationSpec = remember(key1 = isDrawerCurrentlyOpened) {
      if (isDrawerCurrentlyOpened && canUseFancyAnimations) {
        tween<Int>(durationMillis = 500)
      } else {
        snap<Int>()
      }
    }

    val newPostsAnimated by animateIntAsState(
      targetValue = newPosts,
      animationSpec = animationSpec
    )
    val newQuotesAnimated by animateIntAsState(
      targetValue = newQuotes,
      animationSpec = animationSpec
    )
    val totalPostsAnimated by animateIntAsState(
      targetValue = totalPosts,
      animationSpec = animationSpec
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
      val defaultTextColor = if (isDead) {
        chanTheme.textColorHintCompose
      } else {
        chanTheme.textColorSecondaryCompose
      }

      buildAnnotatedString {
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
              append(
                buildAnnotatedString {
                  if (!isDead && newQuotesAnimated > 0) {
                    pushStyle(SpanStyle(color = chanTheme.bookmarkCounterHasRepliesColorCompose))
                  } else {
                    pushStyle(SpanStyle(color = defaultTextColor))
                  }

                  append(" (")
                  append(newQuotesAnimated.toString())
                  append(")")
                }
              )
            }
          }
        )

        if (totalPages > 0) {
          if (length > 0) {
            append(AppConstants.TEXT_SEPARATOR)
          }

          append(
            buildAnnotatedString {
              if (!isDead) {
                if (currentPage >= totalPages) {
                  pushStyle(SpanStyle(color = chanTheme.accentColorCompose))
                }
              }

              append("Pg: ")
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

    KurobaComposeText(
      modifier = modifier,
      color = Color.Unspecified,
      fontSize = 12.sp,
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
            logcatError {
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

  companion object {
    private const val TAG = "BookmarksScreen"
    val SCREEN_KEY = ScreenKey("BookmarksScreen")
  }
}