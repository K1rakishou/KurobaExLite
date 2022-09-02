package com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.github.k1rakishou.composecustomtextselection.lib.ConfigurableTextToolbar
import com.github.k1rakishou.composecustomtextselection.lib.SelectableTextContainer
import com.github.k1rakishou.composecustomtextselection.lib.SelectionToolbarMenu
import com.github.k1rakishou.composecustomtextselection.lib.rememberSelectionState
import com.github.k1rakishou.composecustomtextselection.lib.textSelectionAfterDoubleTapOrTapWithLongTap
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRememberViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.PostIcon
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.Shimmer
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun PostCell(
  isCatalogMode: Boolean,
  chanDescriptor: ChanDescriptor,
  detectLinkableClicks: Boolean,
  postCellCommentTextSizeSp: TextUnit,
  postCellSubjectTextSizeSp: TextUnit,
  postCellData: PostCellData,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
  onPostBind: (PostCellData) -> Unit,
  onPostUnbind: (PostCellData) -> Unit,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String, PostCellData) -> Unit,
  onPostCellCommentClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostCellCommentLongClicked: (PostCellData, AnnotatedString, Int) -> Unit,
  onPostRepliesClicked: (PostCellData) -> Unit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
) {
  val postComment = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostComment }
  val postSubject = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.processedPostSubject }
  val postFooterText = remember(postCellData.parsedPostData) { postCellData.parsedPostData?.postFooterText }

  DisposableEffect(
    key1 = postCellData,
    effect = {
      onPostBind(postCellData)
      onDispose { onPostUnbind(postCellData) }
    })

  Row(
    modifier = Modifier
      .wrapContentHeight()
      .padding(vertical = 8.dp)
  ) {
    var columnSizeMut by remember { mutableStateOf<IntSize?>(null) }
    val columnSize = columnSizeMut

    Column(
      modifier = Modifier
        .weight(1f)
        .onSizeChanged { size -> columnSizeMut = size }
    ) {
      PostCellTitle(
        chanDescriptor = chanDescriptor,
        postCellData = postCellData,
        postSubject = postSubject,
        postCellSubjectTextSizeSp = postCellSubjectTextSizeSp,
        onPostImageClicked = onPostImageClicked,
        reparsePostSubject = reparsePostSubject
      )

      Spacer(modifier = Modifier.height(4.dp))

      PostCellComment(
        postCellData = postCellData,
        postComment = postComment,
        isCatalogMode = isCatalogMode,
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
        postFooterText = postFooterText,
        postCellCommentTextSizeSp = postCellCommentTextSizeSp,
        onPostRepliesClicked = onPostRepliesClicked
      )
    }

    if (columnSize != null) {
      PostCellMarks(columnSize, postCellData)
    }
  }
}

@Composable
private fun PostCellMarks(columnSize: IntSize, postCellData: PostCellData) {
  val parsedPostData = postCellData.parsedPostData
    ?: return

  if (!parsedPostData.isPostMarkedAsMine && !parsedPostData.isReplyToPostMarkedAsMine) {
    return
  }

  val chanTheme = LocalChanTheme.current
  val heightDp = with(LocalDensity.current) { columnSize.height.toDp() }
  val totalCanvasWidth = 3.dp
  val lineWidthPx = with(LocalDensity.current) { remember { totalCanvasWidth.toPx() } }
  val pathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f) }

  Row {
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
}

@Composable
private fun PostCellTitle(
  chanDescriptor: ChanDescriptor,
  postCellData: PostCellData,
  postSubject: AnnotatedString?,
  postCellSubjectTextSizeSp: TextUnit,
  onPostImageClicked: (ChanDescriptor, Result<IPostImage>, Rect) -> Unit,
  reparsePostSubject: (PostCellData, (AnnotatedString?) -> Unit) -> Unit,
) {
  Row(
    modifier = Modifier
      .wrapContentHeight()
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (postCellData.images.isNotNullNorEmpty()) {
      val postImage = postCellData.images.first()
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

      Spacer(modifier = Modifier.width(4.dp))
    }

    if (postSubject == null) {
      Shimmer(
        modifier = Modifier
          .weight(1f)
          .height(42.dp)
      )
    } else {
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
        fontSize = postCellSubjectTextSizeSp,
        inlineContent = inlinedContentForPostCell(
          postCellData = postCellData,
          postCellSubjectTextSizeSp = postCellSubjectTextSizeSp
        )
      )
    }
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
  postComment: AnnotatedString?,
  isCatalogMode: Boolean,
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
  var isInSelectionMode by remember { mutableStateOf(false) }

  if (postComment.isNotNullNorBlank()) {
    PostCellCommentSelectionWrapper(
      isCatalogMode = isCatalogMode,
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
private fun PostCellCommentSelectionWrapper(
  isCatalogMode: Boolean,
  onCopySelectedText: (String) -> Unit,
  onQuoteSelectedText: (Boolean, String) -> Unit,
  onTextSelectionModeChanged: (inSelectionMode: Boolean) -> Unit,
  content: @Composable (textModifier: Modifier, onTextLayout: (TextLayoutResult) -> Unit) -> Unit
) {
  if (isCatalogMode) {
    content(
      textModifier = Modifier,
      onTextLayout = {}
    )
    return
  }

  val view = LocalView.current
  val context = LocalContext.current
  val chanTheme = LocalChanTheme.current
  val selectionState = rememberSelectionState()

  val onCopySelectedTextUpdated by rememberUpdatedState(newValue = onCopySelectedText)
  val onQuoteSelectedTextUpdated by rememberUpdatedState(newValue = onQuoteSelectedText)

  val configurableTextToolbar = remember {
    val resources = context.resources
    val theme = context.theme

    var id = 1
    var order = 0

    val selectionToolbarMenu = SelectionToolbarMenu(
      items = listOf(
        SelectionToolbarMenu.Item(
          id = id++,
          order = order++,
          text = resources.getString(R.string.copy),
          icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_content_copy_24, theme),
          callback = { selectedText -> onCopySelectedTextUpdated.invoke(selectedText.text) }
        ),
        SelectionToolbarMenu.Item(
          id = id++,
          order = order++,
          text = resources.getString(R.string.quote),
          icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_chevron_right_24, theme),
          callback = { selectedText -> onQuoteSelectedTextUpdated.invoke(false, selectedText.text) }
        ),
        SelectionToolbarMenu.Item(
          id = id++,
          order = order++,
          text = resources.getString(R.string.quote_text),
          icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_format_quote_24, theme),
          callback = { selectedText -> onQuoteSelectedTextUpdated.invoke(true, selectedText.text) }
        ),
      )
    )

    return@remember ConfigurableTextToolbar(
      view = view,
      selectionToolbarMenu = selectionToolbarMenu
    )
  }

  val textSelectionColors = remember(key1 = chanTheme.accentColor) {
    TextSelectionColors(
      handleColor = chanTheme.accentColor,
      backgroundColor = chanTheme.accentColor.copy(alpha = 0.4f)
    )
  }

  CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
    SelectableTextContainer(
      modifier = Modifier
        .pointerInput(
          key1 = Unit,
          block = { textSelectionAfterDoubleTapOrTapWithLongTap(selectionState) }
        ),
      selectionState = selectionState,
      configurableTextToolbar = configurableTextToolbar,
      onEnteredSelection = { onTextSelectionModeChanged(true) },
      onExitedSelection = { onTextSelectionModeChanged(false) },
      textContent = { modifier, onTextLayout -> content(modifier, onTextLayout) }
    )
  }
}

@Composable
private fun PostCellFooter(
  postCellData: PostCellData,
  postFooterText: AnnotatedString?,
  postCellCommentTextSizeSp: TextUnit,
  onPostRepliesClicked: (PostCellData) -> Unit
) {
  val chanTheme = LocalChanTheme.current

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