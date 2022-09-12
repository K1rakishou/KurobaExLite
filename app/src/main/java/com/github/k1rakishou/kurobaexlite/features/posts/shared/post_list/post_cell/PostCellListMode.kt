package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell

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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostImageThumbnail
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.createClickableTextColorMap
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.detectClickedAnnotations
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCard
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine


@Composable
fun PostCellListMode(
  chanDescriptor: ChanDescriptor,
  currentlyOpenedThread: ThreadDescriptor?,
  postCellData: PostCellData,
  cellsPadding: PaddingValues,
  postCellSubjectTextSizeSp: TextUnit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
  textSelectionEnabled: Boolean,
  detectLinkableClicks: Boolean,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  postCellCommentTextSizeSp: TextUnit,
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

  val postCellBackgroundColor = remember(
    key1 = isCatalogMode,
    key2 = currentlyOpenedThread,
    key3 = postCellData.postDescriptor
  ) {
    if (isCatalogMode && currentlyOpenedThread == postCellData.postDescriptor.threadDescriptor) {
      chanTheme.highlighterColor.copy(alpha = 0.3f)
    } else {
      Color.Unspecified
    }
  }

  Row(
    modifier = Modifier
      .wrapContentHeight()
      .drawBehind { drawRect(postCellBackgroundColor) }
      .padding(resultPaddings)
  ) {
    var columnHeightMut by remember { mutableStateOf<Int?>(null) }
    val columnHeight = columnHeightMut

    Column(
      modifier = Modifier
        .weight(1f)
        .onSizeChanged { size -> columnHeightMut = size.height }
    ) {
      PostCellTitle(
        chanDescriptor = chanDescriptor,
        postCellData = postCellData,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
        onPostImageClicked = onPostImageClicked,
        reparsePostSubject = reparsePostSubject
      )

      Spacer(modifier = Modifier.height(4.dp))

      PostCellComment(
        postCellData = postCellData,
        textSelectionEnabled = textSelectionEnabled,
        detectLinkableClicks = detectLinkableClicks,
        onCopySelectedText = onCopySelectedText,
        onQuoteSelectedText = onQuoteSelectedText,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        onPostCellCommentClicked = onPostCellCommentClicked,
        onPostCellCommentLongClicked = onPostCellCommentLongClicked,
        onTextSelectionModeChanged = onTextSelectionModeChanged
      )

      PostCellFooter(
        postCellData = postCellData,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
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
  val chanTheme = LocalChanTheme.current
  val heightDp = with(LocalDensity.current) { columnHeight.toDp() }

  Spacer(modifier = Modifier.width(4.dp))

  val cardBgColor = remember(key1 = chanTheme.backColor) {
    if (ThemeEngine.isDarkColor(chanTheme.backColor)) {
      ThemeEngine.manipulateColor(chanTheme.backColor, 1.2f)
    } else {
      ThemeEngine.manipulateColor(chanTheme.backColor, 0.8f)
    }
  }

  KurobaComposeCard(
    modifier = Modifier
      .width(38.dp)
      .height(heightDp)
      .kurobaClickable(bounded = true, onClick = { onGoToPostClicked(postCellData) }),
    backgroundColor = cardBgColor
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
}

@Composable
private fun PostCellTitle(
  chanDescriptor: ChanDescriptor,
  postCellData: PostCellData,
  postCellSubjectTextSizeSp: TextUnit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
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
      PostCellThumbnail(
        images = postCellData.images,
        onPostImageClicked = onPostImageClicked,
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
        reparsePostSubject = reparsePostSubject,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp
      )
    }
  }
}

@Composable
private fun RowScope.PostCellSubject(
  postSubject: AnnotatedString,
  postCellData: PostCellData,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
  postCellSubjectTextSizeSp: TextUnit
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

        val newPostSubject =
          suspendCancellableCoroutine<AnnotatedString?> { cancellableContinuation ->
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
    fontSize = postCellSubjectTextSizeSp,
    inlineContent = inlinedContentForPostCell(
      postCellData = postCellData,
      postCellSubjectTextSizeSp = postCellSubjectTextSizeSp
    )
  )
}

@Composable
private fun PostCellThumbnail(
  images: List<PostCellImageData>,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  chanDescriptor: ChanDescriptor
) {
  val postImage = images.first()
  var boundsInWindowMut by remember { mutableStateOf<Rect?>(null) }

  Box(
    modifier = Modifier
      .wrapContentSize()
      .onGloballyPositioned { layoutCoordinates ->
        boundsInWindowMut = layoutCoordinates.boundsInWindow()
      }
  ) {
    PostImageThumbnail(
      modifier = Modifier.size(70.dp),
      postImage = postImage,
      onClickWithError = { clickedImageResult ->
        val boundsInWindow = boundsInWindowMut
          ?: return@PostImageThumbnail

        onPostImageClicked(chanDescriptor, clickedImageResult, boundsInWindow)
      }
    )
  }
}

@Composable
fun inlinedContentForPostCell(
  postCellData: PostCellData,
  postCellSubjectTextSizeSp: TextUnit
): Map<String, InlineTextContent> {
  return remember(
    postCellData.archived,
    postCellData.deleted,
    postCellData.closed,
    postCellData.sticky,
    postCellData.countryFlag,
    postCellData.boardFlag,
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

  var iconUrl by remember { mutableStateOf<String?>(null) }

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

      iconUrl = postCellIconViewModel.formatIconUrl(
        postDescriptor = postDescriptor,
        postIcon = flag
      )
    }
  )

  if (iconUrl == null) {
    return
  }

  AsyncImage(
    modifier = Modifier.fillMaxSize(),
    model = ImageRequest.Builder(context)
      .data(iconUrl)
      .crossfade(true)
      .size(Size.ORIGINAL)
      .build(),
    contentDescription = "poster flag"
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
      ParsedPostDataCache.PostCellIcon.RollingSticky -> R.drawable.cyclic_icon
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
  textSelectionEnabled: Boolean,
  detectLinkableClicks: Boolean,
  postCellCommentTextSizeSp: TextUnit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val clickedTextBackgroundColorMap = remember(key1 = chanTheme) { createClickableTextColorMap(chanTheme) }
  val postComment = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostComment }
  var isInSelectionMode by remember { mutableStateOf(false) }

  if (postComment.isNotNullNorBlank()) {
    PostCellCommentSelectionWrapper(
      textSelectionEnabled = textSelectionEnabled,
      onCopySelectedText = onCopySelectedText,
      onQuoteSelectedText = { withText, selectedText -> onQuoteSelectedText(withText, selectedText, postCellData) },
      onTextSelectionModeChanged = { inSelectionMode ->
        isInSelectionMode = inSelectionMode
        onTextSelectionModeChanged(inSelectionMode)
      }
    ) { textModifier, onTextLayout ->
      KurobaComposeClickableText(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .then(textModifier),
        fontSize = postCellCommentTextSizeSp,
        text = postComment,
        isTextClickable = detectLinkableClicks && !isInSelectionMode,
        annotationBgColors = clickedTextBackgroundColorMap,
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
  postCellCommentTextSizeSp: TextUnit,
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
      Text(
        modifier = Modifier
          .weight(1f)
          .wrapContentHeight()
          .kurobaClickable(onClick = { onPostRepliesClicked(postCellData) })
          .padding(vertical = 4.dp),
        color = chanTheme.textColorSecondary,
        fontSize = postCellCommentTextSizeSp,
        text = postFooterText
      )
    } else {
      Spacer(modifier = Modifier.weight(1f))
    }
  }

}