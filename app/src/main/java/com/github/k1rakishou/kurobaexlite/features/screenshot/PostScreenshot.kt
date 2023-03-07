package com.github.k1rakishou.kurobaexlite.features.screenshot

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.PostListOptions
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.PostCell
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.post_cell.rememberPostBlinkAnimationState
import com.github.k1rakishou.kurobaexlite.features.posts.shared.post_list.rememberPostListSelectionState
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.screenshot.ScreenshotResult
import com.github.k1rakishou.kurobaexlite.helpers.screenshot.screenshot
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.parallelForEach
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeDivider
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.themes.ChanTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@Stable
class PostScreenshot {
  private val screenshotData = mutableStateOf<ScreenshotData?>(null)
  private val _composableBounds = mutableStateOf<Rect?>(null)
  val composableBounds: State<Rect?>
    get() = _composableBounds

  private var callback: ((Int, CompletableDeferred<Result<Bitmap>>) -> Unit)? = null

  private val parsedPostDataRepository: ParsedPostDataRepository by inject(ParsedPostDataRepository::class.java)

  @Composable
  fun ScreenshotContainer(
    chanDescriptor: ChanDescriptor,
    postListOptions: PostListOptions
  ) {
    val screenshotDataMut by screenshotData
    val screenshotData = screenshotDataMut

    if (screenshotData == null) {
      return
    }

    val view = LocalView.current
    val chanTheme = LocalChanTheme.current
    val cellsPadding = remember { PaddingValues(horizontal = 8.dp) }
    val postBlinkAnimationState = rememberPostBlinkAnimationState()
    val composableBounds by _composableBounds

    DisposableEffect(
      key1 = composableBounds,
      effect = {
        val localComposableBounds = composableBounds
        if (localComposableBounds != null) {
          callback = { requiredHeight, completableDeferred ->
            val currentHeight = localComposableBounds.height
            val minHeight = Math.min(currentHeight, requiredHeight.toFloat())
            val heightDelta = (currentHeight - minHeight).coerceAtLeast(0f)
            val updatedBounds = localComposableBounds.copy(top = localComposableBounds.top + heightDelta)

            view.screenshot(
              bounds = updatedBounds,
              bitmapCallback = { screenshotResult ->
                when (screenshotResult) {
                  ScreenshotResult.Initial -> {
                    // no-op
                  }
                  is ScreenshotResult.Error -> {
                    logcatError(TAG) { "view.screenshot() error: ${screenshotResult.exception.asLogIfImportantOrErrorMessage()}" }
                    completableDeferred.complete(Result.failure(screenshotResult.exception))
                  }
                  is ScreenshotResult.Success -> {
                    logcat(TAG) { "view.screenshot() success" }
                    completableDeferred.complete(Result.success(screenshotResult.data))
                  }
                }
              }
            )
          }
        }

        onDispose { callback = null }
      }
    )

    val postListSelectionState = rememberPostListSelectionState(postSelectionEnabled = false)

    Column(
      modifier = Modifier
        .wrapContentSize()
        .drawBehind { drawRect(chanTheme.backColor) }
        .onGloballyPositioned {
          _composableBounds.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            it.boundsInWindow()
          } else {
            it.boundsInRoot()
          }
        }
    ) {
      screenshotData.posts.forEachIndexed { index, postCellData ->
        key(postCellData.postDescriptor) {
          PostCell(
            postViewMode = postListOptions.postViewMode,
            isCatalogMode = postListOptions.isCatalogMode,
            textSelectionEnabled = false,
            chanDescriptor = chanDescriptor,
            currentlyOpenedThread = null,
            detectLinkableClicks = false,
            postCellData = postCellData,
            cellsPadding = cellsPadding,
            postListSelectionState = postListSelectionState,
            postBlinkAnimationState = postBlinkAnimationState,
            onTextSelectionModeChanged = { _ -> },
            onPostBind = { },
            onPostUnbind = { },
            onCopySelectedText = { },
            onQuoteSelectedText = { _, _, _ -> },
            onPostCellCommentClicked = { _, _, _ -> },
            onPostCellCommentLongClicked = { _, _, _ -> },
            onPostRepliesClicked = { _ -> },
            onPostImageClicked = { _, _, _ -> },
            onPostImageLongClicked = { _, _ -> },
            onGoToPostClicked = null,
            reparsePostSubject = { _, _ -> }
          )

          if (postListOptions.postViewMode == PostViewMode.List && index < screenshotData.posts.lastIndex) {
            KurobaComposeDivider(
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }
  }

  suspend fun init(chanTheme: ChanTheme, posts: List<PostCellData>): Boolean {
    if (screenshotData.value != null) {
      return false
    }

    val parallelization = AppConstants.coresCount.coerceAtLeast(2)

    val updatedPosts = parallelForEach(
      dataList = posts,
      parallelization = parallelization,
      dispatcher = Dispatchers.Default
    ) { postCellData ->
      val oldParsedPostData = postCellData.parsedPostData
        ?: return@parallelForEach postCellData

      val updateParsedPostDataContext = oldParsedPostData.parsedPostDataContext.copy(revealFullPostComment = true)

      val newParsedPostData = parsedPostDataRepository.calculateParsedPostData(
        postCellData = postCellData,
        parsedPostDataContext = updateParsedPostDataContext,
        chanTheme = chanTheme
      )

      return@parallelForEach postCellData.copy(parsedPostData = newParsedPostData)
    }

    screenshotData.value = ScreenshotData(updatedPosts)
    return true
  }

  suspend fun performScreenshot(requiredHeight: Int?): Result<Bitmap?> {
    coroutineScope {
      while (callback == null && isActive) {
        delay(25)
      }

      ensureActive()
    }

    awaitFrame()

    val height = requiredHeight
      ?: composableBounds.value?.height?.toInt()
      ?: return Result.success(null)

    val completableDeferred = CompletableDeferred<Result<Bitmap>>()
    callback!!(height, completableDeferred)

    return completableDeferred.await()
  }

  private data class ScreenshotData(
    val posts: List<PostCellData>
  )

  companion object {
    private const val TAG = "PostScreenshot"
  }

}