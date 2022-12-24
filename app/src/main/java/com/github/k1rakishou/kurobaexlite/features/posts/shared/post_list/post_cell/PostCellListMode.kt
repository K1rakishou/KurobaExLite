package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

import android.content.Context
import androidx.compose.animation.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostImageThumbnail
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListSelectionState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.createClickableTextColorMap
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.detectClickedAnnotations
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors.Chan4MathTagProcessor
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors.IPostProcessor
import com.github.k1rakishou.kurobaexlite.helpers.util.asReadableFileSize
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.helpers.util.substringSafe
import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.FlowRow
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCustomUnitText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.coerceIn
import com.github.k1rakishou.kurobaexlite.ui.helpers.collectTextFontSize
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.*

private val ThumbnailSize = 70.dp

@Composable
fun PostCellListMode(
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  postCellData: PostCellData,
  cellsPadding: PaddingValues,
  postListSelectionState: PostListSelectionState,
  postBlinkAnimationState: PostBlinkAnimationState,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
  textSelectionEnabled: Boolean,
  detectLinkableClicks: Boolean,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onGoToPostClicked: ((PostCellData) -> Unit)?
) {
  val chanTheme = LocalChanTheme.current
  val additionalVerticalPadding = 8.dp
  val isCatalogMode = chanDescriptor is CatalogDescriptor

  val resultPaddings = remember(key1 = cellsPadding) {
    return@remember PaddingValues(
      start = cellsPadding.calculateStartPadding(LayoutDirection.Ltr),
      end = cellsPadding.calculateEndPadding(LayoutDirection.Ltr),
      top = cellsPadding.calculateTopPadding() + additionalVerticalPadding,
      bottom = cellsPadding.calculateBottomPadding() + additionalVerticalPadding
    )
  }

  val highlightColorWithAlpha = remember(key1 = chanTheme.highlightedOnBackColor) { chanTheme.highlightedOnBackColor.copy(alpha = 0.3f) }
  val selectColorWithAlpha = remember(key1 = chanTheme.selectedOnBackColor) { chanTheme.selectedOnBackColor.copy(alpha = 0.5f) }

  val isInPostSelectionMode by postListSelectionState.isInSelectionMode
  val isPostSelected = if (isInPostSelectionMode) {
    postListSelectionState.isPostSelected(postCellData.postDescriptor)
  } else {
    false
  }

  val postCellBackgroundColor = remember(
    isCatalogMode,
    currentlyOpenedThread,
    postCellData.postDescriptor,
    isInPostSelectionMode,
    isPostSelected,
    selectColorWithAlpha,
    highlightColorWithAlpha
  ) {
    if (isInPostSelectionMode) {
      if (isPostSelected) {
        return@remember selectColorWithAlpha
      }

      return@remember Color.Unspecified
    }

    if (isCatalogMode && currentlyOpenedThread == postCellData.postDescriptor.threadDescriptor) {
      return@remember highlightColorWithAlpha
    }

    return@remember Color.Unspecified
  }

  val postCellBackgroundColorAnimatable = remember(postCellBackgroundColor) { Animatable(initialValue = postCellBackgroundColor) }
  val postCellBackgroundColorAnimatableProvider = remember(postCellBackgroundColor) { { postCellBackgroundColorAnimatable } }

  BlinkAnimation(
    postCellDefaultBgColor = postCellBackgroundColor,
    postCellBlinkBgColor = highlightColorWithAlpha,
    postDescriptor = postCellData.postDescriptor,
    postBlinkAnimationState = postBlinkAnimationState,
    postCellBackgroundColorAnimatableProvider = postCellBackgroundColorAnimatableProvider
  )

  Row(
    modifier = Modifier
      .wrapContentHeight()
      .drawBehind { drawRect(postCellBackgroundColorAnimatable.value) }
      .padding(resultPaddings)
  ) {
    var columnHeightMut by remember { mutableStateOf<Int?>(null) }
    val columnHeight = columnHeightMut

    Column(
      modifier = Modifier
        .weight(1f)
        .onSizeChanged { size -> columnHeightMut = size.height }
    ) {
      val imagesCount = postCellData.images?.size ?: 0

      if (imagesCount <= 1) {
        PostCellTitleZeroOrOneThumbnails(
          chanDescriptor = chanDescriptor,
          postCellData = postCellData,
          postListSelectionState = postListSelectionState,
          onPostImageClicked = onPostImageClicked,
          onPostImageLongClicked = onPostImageLongClicked,
          reparsePostSubject = reparsePostSubject
        )
      } else {
        PostCellTitleTwoOrMoreThumbnails(
          chanDescriptor = chanDescriptor,
          postCellData = postCellData,
          postListSelectionState = postListSelectionState,
          onPostImageClicked = onPostImageClicked,
          onPostImageLongClicked = onPostImageLongClicked,
          reparsePostSubject = reparsePostSubject
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      PostCellComment(
        postCellData = postCellData,
        isInPostSelectionMode = isInPostSelectionMode,
        textSelectionEnabled = textSelectionEnabled,
        detectLinkableClicks = detectLinkableClicks,
        onCopySelectedText = onCopySelectedText,
        onQuoteSelectedText = onQuoteSelectedText,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostCellCommentLongClicked = onPostCellCommentLongClicked,
        onTextSelectionModeChanged = onTextSelectionModeChanged
      )

      PostCellFooter(
        postCellData = postCellData,
        onPostRepliesClicked = onPostRepliesClicked
      )
    }

    if (columnHeight != null) {
      PostCellMarks(
        columnHeight = columnHeight,
        postCellData = postCellData
      )

      if (onGoToPostClicked != null) {
        GoToPostButton(
          columnHeight = columnHeight,
          onGoToPostClicked = onGoToPostClicked,
          postCellData = postCellData
        )
      }
    }
  }
}

@Composable
private fun RowScope.GoToPostButton(
  columnHeight: Int,
  onGoToPostClicked: (PostCellData) -> Unit,
  postCellData: PostCellData
) {
  val heightDp = with(LocalDensity.current) { columnHeight.toDp() }

  Spacer(modifier = Modifier.width(4.dp))

  KurobaComposeCard(
    modifier = Modifier
      .width(38.dp)
      .height(heightDp)
      .kurobaClickable(bounded = true, onClick = { onGoToPostClicked(postCellData) })
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeIcon(
        modifier = Modifier.size(28.dp),
        drawableId = R.drawable.ic_baseline_chevron_right_24
      )
    }
  }

  Spacer(modifier = Modifier.width(4.dp))
}

@Composable
private fun RowScope.PostCellMarks(columnHeight: Int, postCellData: PostCellData) {
  val parsedPostData = postCellData.parsedPostData
    ?: return

  if (!parsedPostData.isPostMarkedAsMine && !parsedPostData.isReplyToPostMarkedAsMine) {
    return
  }

  val chanTheme = LocalChanTheme.current
  val heightDp = with(LocalDensity.current) { columnHeight.toDp() }
  val totalCanvasWidth = 3.dp
  val lineWidthPx = with(LocalDensity.current) { remember { totalCanvasWidth.toPx() } }
  val pathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f) }

  Spacer(modifier = Modifier.width(4.dp))

  Canvas(
    modifier = Modifier
      .width(totalCanvasWidth)
      .height(heightDp),
    onDraw = {
      when {
        parsedPostData.isPostMarkedAsMine -> {
          drawLine(
            color = chanTheme.postSavedReplyColor,
            strokeWidth = lineWidthPx,
            start = Offset.Zero,
            end = Offset(0f, size.height)
          )
        }
        parsedPostData.isReplyToPostMarkedAsMine -> {
          drawLine(
            color = chanTheme.postSavedReplyColor,
            strokeWidth = lineWidthPx,
            start = Offset.Zero,
            end = Offset(0f, size.height),
            pathEffect = pathEffect
          )
        }
        else -> {
          unreachable()
        }
      }
    }
  )

  Spacer(modifier = Modifier.width(4.dp))
}

@Composable
private fun PostCellTitleZeroOrOneThumbnails(
  chanDescriptor: ChanDescriptor,
  postCellData: PostCellData,
  postListSelectionState: PostListSelectionState,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
) {
  val postSubject = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostSubject }

  Row(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (postCellData.images.isNotNullNorEmpty()) {
      val postImage = postCellData.images.first()

      PostCellThumbnail(
        thumbnailSize = ThumbnailSize,
        postImage = postImage,
        postListSelectionState = postListSelectionState,
        onPostImageClicked = onPostImageClicked,
        onPostImageLongClicked = onPostImageLongClicked,
        chanDescriptor = chanDescriptor
      )

      Spacer(modifier = Modifier.width(4.dp))
    }

    if (postSubject == null) {
      Shimmer(
        modifier = Modifier
          .weight(1f)
          .height(42.dp)
      )
    } else {
      PostCellSubject(
        postSubject = postSubject,
        postCellData = postCellData,
        reparsePostSubject = reparsePostSubject
      )
    }
  }
}

@Composable
private fun PostCellTitleTwoOrMoreThumbnails(
  chanDescriptor: ChanDescriptor,
  postCellData: PostCellData,
  postListSelectionState: PostListSelectionState,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val postSubject = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostSubject }

  Column(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
  ) {
    if (postSubject == null) {
      Shimmer(
        modifier = Modifier
          .weight(1f)
          .height(42.dp)
      )
    } else {
      Row {
        PostCellSubject(
          postSubject = postSubject,
          postCellData = postCellData,
          reparsePostSubject = reparsePostSubject
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    FlowRow(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth(),
      mainAxisSpacing = 16.dp,
      crossAxisSpacing = 8.dp
    ) {
      for (postImage in postCellData.images!!) {
        key(postImage.fullImageUrl) {
          Column {
            PostCellThumbnail(
              thumbnailSize = ThumbnailSize,
              postImage = postImage,
              postListSelectionState = postListSelectionState,
              onPostImageClicked = onPostImageClicked,
              onPostImageLongClicked = onPostImageLongClicked,
              chanDescriptor = chanDescriptor
            )

            val postImageInfo = remember(key1 = postImage) {
              buildString {
                append(postImage.ext.uppercase(Locale.ENGLISH))
                appendLine()
                append(postImage.width.toString())
                append("x")
                append(postImage.height.toString())
                appendLine()
                append(postImage.fileSize.asReadableFileSize())
              }
            }

            KurobaComposeCustomUnitText(
              modifier = Modifier
                .width(ThumbnailSize)
                .wrapContentHeight(),
              text = postImageInfo,
              color = chanTheme.postDetailsColor,
              textAlign = TextAlign.Center,
              fontSize = 12.sp.coerceIn(min = 11.sp, max = 14.sp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RowScope.PostCellSubject(
  postSubject: AnnotatedString,
  postCellData: PostCellData,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit
) {
  var actualPostSubject by remember(key1 = postSubject) { mutableStateOf(postSubject) }

  LaunchedEffect(
    key1 = postCellData,
    block = {
      val initialTime = postCellData.timeMs
        ?: return@LaunchedEffect

      while (isActive) {
        val now = System.currentTimeMillis()
        val delay = if (now - initialTime <= 60_000L) 5000L else 60_000L

        delay(delay)

        val newPostSubject = suspendCancellableCoroutine<AnnotatedString?> { cancellableContinuation ->
          reparsePostSubject(postCellData) { parsedPostSubject ->
            cancellableContinuation.resumeSafe(
              parsedPostSubject
            )
          }
        }

        if (newPostSubject == null) {
          return@LaunchedEffect
        }

        actualPostSubject = newPostSubject
      }
    })

  Text(
    modifier = Modifier.weight(1f),
    text = actualPostSubject,
    inlineContent = inlinedContentForPostCell(
      postCellData = postCellData
    )
  )
}

@Composable
private fun PostCellThumbnail(
  thumbnailSize: Dp,
  postListSelectionState: PostListSelectionState,
  postImage: PostCellImageData,
  onPostImageClicked: (ChanDescriptor, IPostImage, Rect) -> Unit,
  onPostImageLongClicked: (ChanDescriptor, IPostImage) -> Unit,
  chanDescriptor: ChanDescriptor
) {
  var boundsInWindowMut by remember { mutableStateOf<Rect?>(null) }
  val isInSelectionMode by postListSelectionState.isInSelectionMode

  Box(
    modifier = Modifier
      .wrapContentSize()
      .onGloballyPositioned { layoutCoordinates ->
        boundsInWindowMut = layoutCoordinates.boundsInWindow()
      }
  ) {
    PostImageThumbnail(
      modifier = Modifier.size(thumbnailSize),
      postImage = postImage,
      onClick = { clickedImage ->
        if (isInSelectionMode) {
          postListSelectionState.toggleSelection(postImage.ownerPostDescriptor)
          return@PostImageThumbnail
        }

        val boundsInWindow = boundsInWindowMut
          ?: return@PostImageThumbnail

        onPostImageClicked(chanDescriptor, clickedImage, boundsInWindow)
      },
      onLongClick = { longClickedImage ->
        if (isInSelectionMode) {
          postListSelectionState.toggleSelection(postImage.ownerPostDescriptor)
          return@PostImageThumbnail
        }

        onPostImageLongClicked(chanDescriptor, longClickedImage)
      }
    )
  }
}

@Composable
fun inlinedContentForPostCell(
  postCellData: PostCellData
): Map<String, InlineTextContent> {
  val postCellSubjectTextSizeSp = collectTextFontSize(defaultFontSize = 14.sp)

  return remember(
    postCellData.archived,
    postCellData.deleted,
    postCellData.closed,
    postCellData.sticky,
    postCellData.countryFlag,
    postCellData.boardFlag,
    postCellSubjectTextSizeSp
  ) {
    if (
      !postCellData.archived &&
      !postCellData.deleted &&
      !postCellData.closed &&
      postCellData.sticky == null &&
      postCellData.countryFlag == null &&
      postCellData.boardFlag == null
    ) {
      return@remember emptyMap<String, InlineTextContent>()
    }

    val resultMap = mutableMapOf<String, InlineTextContent>()

    ParsedPostDataCache.PostCellIcon.values().forEach { postCellAnnotatedContent ->
      resultMap[postCellAnnotatedContent.id] = InlineTextContent(
        placeholder = Placeholder(
          width = postCellSubjectTextSizeSp,
          height = postCellSubjectTextSizeSp,
          placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        ),
        children = {
          when (postCellAnnotatedContent) {
            ParsedPostDataCache.PostCellIcon.Deleted,
            ParsedPostDataCache.PostCellIcon.Closed,
            ParsedPostDataCache.PostCellIcon.Archived,
            ParsedPostDataCache.PostCellIcon.RollingSticky,
            ParsedPostDataCache.PostCellIcon.Sticky -> {
              PostCellIcon(postCellAnnotatedContent)
            }
            ParsedPostDataCache.PostCellIcon.CountryFlag -> {
              if (postCellData.countryFlag != null) {
                PostCellIcon(
                  postDescriptor = postCellData.postDescriptor,
                  flag = postCellData.countryFlag,
                  postCellAnnotatedContent = postCellAnnotatedContent
                )
              }
            }
            ParsedPostDataCache.PostCellIcon.BoardFlag -> {
              if (postCellData.boardFlag != null) {
                PostCellIcon(
                  postDescriptor = postCellData.postDescriptor,
                  flag = postCellData.boardFlag,
                  postCellAnnotatedContent = postCellAnnotatedContent
                )
              }
            }
          }
        }
      )
    }

    return@remember resultMap
  }
}

@Composable
private fun PostCellIcon(
  postDescriptor: PostDescriptor,
  flag: PostIcon,
  postCellAnnotatedContent: ParsedPostDataCache.PostCellIcon
) {
  val context = LocalContext.current
  val postCellIconViewModel = koinRememberViewModel<PostCellIconViewModel>()
  val siteManager = koinRemember<SiteManager>()

  var iconUrlMut by remember { mutableStateOf<HttpUrl?>(null) }
  val iconUrl = iconUrlMut

  LaunchedEffect(
    key1 = Unit,
    block = {
      when (postCellAnnotatedContent) {
        ParsedPostDataCache.PostCellIcon.Deleted,
        ParsedPostDataCache.PostCellIcon.Closed,
        ParsedPostDataCache.PostCellIcon.Archived,
        ParsedPostDataCache.PostCellIcon.Sticky -> {
          error("Expected CountryFlag or BoardFlag but got ${postCellAnnotatedContent}")
        }
        ParsedPostDataCache.PostCellIcon.CountryFlag,
        ParsedPostDataCache.PostCellIcon.BoardFlag,
        ParsedPostDataCache.PostCellIcon.RollingSticky -> {
          // no-op
        }
      }

      iconUrlMut = postCellIconViewModel.formatIconUrl(
        postDescriptor = postDescriptor,
        postIcon = flag
      )?.toHttpUrlOrNull()
    }
  )

  if (iconUrl == null) {
    return
  }

  val imageRequest by produceState<ImageRequest?>(
    initialValue = null,
    key1 = iconUrl,
    producer = {
      value = ImageRequest.Builder(context)
        .data(iconUrl)
        .crossfade(true)
        .size(Size.ORIGINAL)
        .also { imageRequestBuilder ->
          siteManager.bySiteKey(postDescriptor.siteKey)
            ?.requestModifier()
            ?.modifyCoilImageRequest(iconUrl, imageRequestBuilder)
        }
        .build()
    }
  )

  if (imageRequest == null) {
    return
  }

  SubcomposeAsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = imageRequest,
    content = {
      val state = painter.state
      if (state is AsyncImagePainter.State.Error) {
        logcatError("PostCellIcon") {
          "Failed to load icon \'${iconUrl}\', error: ${state.result.throwable.errorMessageOrClassName()}"
        }

        Image(
          modifier = Modifier.fillMaxSize(),
          painter = painterResource(id = R.drawable.error_icon),
          contentDescription = "Error loading poster flag"
        )

      } else if (state is AsyncImagePainter.State.Success) {
        SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
      }
    },
    contentDescription = "Poster flag"
  )
}

@Composable
private fun PostCellIcon(postCellIcon: ParsedPostDataCache.PostCellIcon) {
  val drawableId = remember(key1 = postCellIcon) {
    when (postCellIcon) {
      ParsedPostDataCache.PostCellIcon.Deleted -> R.drawable.trash_icon
      ParsedPostDataCache.PostCellIcon.Archived -> R.drawable.archived_icon
      ParsedPostDataCache.PostCellIcon.Closed -> R.drawable.closed_icon
      ParsedPostDataCache.PostCellIcon.Sticky -> R.drawable.sticky_icon
      ParsedPostDataCache.PostCellIcon.RollingSticky -> R.drawable.rolling_sticky_icon
      else -> error("Unexpected postCellIcon: ${postCellIcon}")
    }
  }

  Image(
    modifier = Modifier.fillMaxSize(),
    painter = painterResource(id = drawableId),
    contentDescription = null
  )
}

@Composable
private fun PostCellComment(
  postCellData: PostCellData,
  isInPostSelectionMode: Boolean,
  textSelectionEnabled: Boolean,
  detectLinkableClicks: Boolean,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val clickedTextBackgroundColorMap = remember(key1 = chanTheme) { createClickableTextColorMap(chanTheme) }
  val postComment = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostComment }
  var isInTextSelectionMode by remember { mutableStateOf(false) }

  if (postComment.isNotNullNorBlank()) {
    PostCellCommentSelectionWrapper(
      textSelectionEnabled = textSelectionEnabled && !isInPostSelectionMode,
      onCopySelectedText = onCopySelectedText,
      onQuoteSelectedText = { withText, selectedText -> onQuoteSelectedText(withText, selectedText, postCellData) },
      onTextSelectionModeChanged = { inSelectionMode ->
        isInTextSelectionMode = inSelectionMode
        onTextSelectionModeChanged(inSelectionMode)
      }
    ) { textModifier, onTextLayout ->
      val isTextClickable = detectLinkableClicks && !isInTextSelectionMode && !isInPostSelectionMode

      KurobaComposeClickableText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .then(textModifier),
        text = postComment,
        isTextClickable = isTextClickable,
        annotationBgColors = clickedTextBackgroundColorMap,
        inlineContent = inlinedContentForPostCellComment(postCellData),
        detectClickedAnnotations = { offset, textLayoutResult, text ->
          return@KurobaComposeClickableText detectClickedAnnotations(offset, textLayoutResult, text)
        },
        onTextAnnotationClicked = { text, offset -> onPostCellCommentClicked(postCellData, text, offset) },
        onTextAnnotationLongClicked = { text, offset -> onPostCellCommentLongClicked(postCellData, text, offset) },
        onTextLayout = onTextLayout
      )
    }
  } else if (postComment == null) {
    Shimmer(
      modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)
    )
  }
}

@Composable
private fun PostCellFooter(
  postCellData: PostCellData,
  onPostRepliesClicked: (PostCellData) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val postFooterText = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.postFooterText }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (postFooterText.isNotNullNorEmpty()) {
      KurobaComposeText(
        modifier = Modifier
          .weight(1f)
          .wrapContentHeight()
          .kurobaClickable(onClick = { onPostRepliesClicked(postCellData) })
          .padding(vertical = 4.dp),
        color = chanTheme.textColorSecondary,
        fontSize = 16.sp,
        text = postFooterText
      )
    } else {
      Spacer(modifier = Modifier.weight(1f))
    }
  }

}

@Composable
private fun inlinedContentForPostCellComment(postCellData: PostCellData): ImmutableMap<String, InlineTextContent> {
  val processedPostComment = postCellData.parsedPostData?.processedPostComment
  if (processedPostComment == null) {
    return persistentMapOf()
  }

  val context = LocalContext.current
  val chan4MathTagProcessor = koinRemember<Chan4MathTagProcessor>()

  var formulas by remember { mutableStateOf<Map<String, CachedFormulaUi>>(emptyMap()) }

  LaunchedEffect(
    key1 = processedPostComment,
    block = {
      val inlinedImages = processedPostComment.getStringAnnotations(
        tag = IPostProcessor.INLINE_CONTENT_TAG,
        start = 0,
        end = processedPostComment.length
      ).filter { range -> range.item.startsWith("${PostCommentApplier.ANNOTATION_INLINED_IMAGE}:") }

      if (inlinedImages.isEmpty()) {
        return@LaunchedEffect
      }

      val foundFormulas = mutableMapOf<String, CachedFormulaUi>()

      inlinedImages.forEach { inlinedImage ->
        val formulaRaw = processedPostComment.text.substringSafe(inlinedImage.start, inlinedImage.end)
        if (formulaRaw.isNullOrBlank()) {
          return@forEach
        }

        val cachedFormula = chan4MathTagProcessor.getCachedFormulaBySanitizedRawFormula(
          postDescriptor = postCellData.postDescriptor,
          formulaRaw = formulaRaw
        )

        if (cachedFormula == null) {
          return@forEach
        }

        foundFormulas[formulaRaw] = CachedFormulaUi(
          formulaRaw = cachedFormula.formulaRaw,
          formulaImageUrl = cachedFormula.formulaImageUrl,
          imageWidth = cachedFormula.imageWidth,
          imageHeight = cachedFormula.imageHeight,
        )
      }

      formulas = foundFormulas
    }
  )

  if (formulas.isEmpty()) {
    return persistentMapOf()
  }

  return remember(key1 = formulas) {
    val map = mutableMapOf<String, InlineTextContent>()

    formulas.entries.forEach { (_, cachedFormulaUi) ->
      val inlinedContentKey = cachedFormulaUi.inlinedContentKey()
      val inlineTextContent = InlineTextContent(
        placeholder = Placeholder(
          width = cachedFormulaUi.imageWidth.sp,
          height = cachedFormulaUi.imageHeight.sp,
          placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        ),
        children = { mathFormulaRaw ->
          FormulaInlinedContent(
            mathFormulaRaw = mathFormulaRaw,
            chan4MathTagProcessor = chan4MathTagProcessor,
            postCellData = postCellData,
            context = context
          )
        }
      )

      map[inlinedContentKey] = inlineTextContent
    }

    return@remember map.toImmutableMap()
  }
}

@Composable
private fun FormulaInlinedContent(
  mathFormulaRaw: String,
  chan4MathTagProcessor: Chan4MathTagProcessor,
  postCellData: PostCellData,
  context: Context
) {
  val imageRequest by produceState<ImageRequest?>(
    initialValue = null,
    key1 = mathFormulaRaw,
    producer = {
      val mathFormulaImageUrl = chan4MathTagProcessor.getCachedFormulaBySanitizedRawFormula(
        postDescriptor = postCellData.postDescriptor,
        formulaRaw = mathFormulaRaw
      )?.formulaImageUrl

      value = if (mathFormulaImageUrl == null) {
        null
      } else {
        ImageRequest.Builder(context)
          .data(mathFormulaImageUrl)
          .size(Size.ORIGINAL)
          .build()
      }
    }
  )

  if (imageRequest != null) {
    AsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = imageRequest,
      contentDescription = "Math formula image"
    )
  }
}