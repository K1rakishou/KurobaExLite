package com.github.k1rakishou.kurobaexlite.managers

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarClickable
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfoEvent
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarType
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

class SnackbarManager(
  private val appContext: Context
) {
  private val _snackbarEventFlow = MutableSharedFlow<SnackbarInfoEvent>(extraBufferCapacity = Channel.UNLIMITED)
  val snackbarEventFlow: SharedFlow<SnackbarInfoEvent>
    get() = _snackbarEventFlow.asSharedFlow()

  private val _activeSnackbarsFlow = MutableStateFlow<Map<ScreenKey, List<SnackbarInfo>>>(emptyMap())

  private val _removedSnackbarsFlow = MutableSharedFlow<Set<RemovedSnackbarInfo>>(extraBufferCapacity = Channel.UNLIMITED)
  val removedSnackbarsFlow: SharedFlow<Set<RemovedSnackbarInfo>>
    get() = _removedSnackbarsFlow.asSharedFlow()

  private val _snackbarElementsClickFlow = MutableSharedFlow<SnackbarClickable>(extraBufferCapacity = Channel.UNLIMITED)
  val snackbarElementsClickFlow: SharedFlow<SnackbarClickable>
    get() = _snackbarElementsClickFlow.asSharedFlow()

  fun onSnackbarElementClicked(snackbarClickable: SnackbarClickable) {
    _snackbarElementsClickFlow.tryEmit(snackbarClickable)
  }

  fun onSnackbarsUpdated(snackbarsByScreenKey: Map<ScreenKey, List<SnackbarInfo>>) {
    _activeSnackbarsFlow.tryEmit(snackbarsByScreenKey)
  }

  fun onSnackbarsRemoved(removedSnackbars: Set<RemovedSnackbarInfo>) {
    _removedSnackbarsFlow.tryEmit(removedSnackbars)
  }

  fun listenForActiveSnackbarsFlow(forScreenKey: ScreenKey): Flow<List<SnackbarInfo>> {
    return _activeSnackbarsFlow.map { map ->
      val thisScreenSnackbars = map[forScreenKey] ?: emptyList()
      val mainScreenSnackbars = map[MainScreen.SCREEN_KEY] ?: emptyList()

      return@map thisScreenSnackbars + mainScreenSnackbars
    }
  }

  fun screenKeyFromDescriptor(chanDescriptor: ChanDescriptor): ScreenKey {
    return when (chanDescriptor) {
      is CatalogDescriptor -> CatalogScreen.SCREEN_KEY
      is ThreadDescriptor -> ThreadScreen.SCREEN_KEY
    }
  }

  // A toast is a snack too
  fun toast(
    @StringRes messageId: Int,
    screenKey: ScreenKey = MainScreen.SCREEN_KEY,
    toastId: String = nextToastId(),
    duration: Duration = STANDARD_DELAY.milliseconds
  ) {
    toast(
      message = appContext.getString(messageId),
      screenKey = screenKey,
      toastId = toastId,
      duration = duration
    )
  }

  fun toast(
    message: String,
    screenKey: ScreenKey = MainScreen.SCREEN_KEY,
    toastId: String = nextToastId(),
    duration: Duration = STANDARD_DELAY.milliseconds
  ) {
    pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.Toast(toastId),
        aliveUntil = SnackbarInfo.snackbarDuration(duration),
        screenKey = screenKey,
        content = listOf(
          SnackbarContentItem.Text(
            text = message,
            takeWholeWidth = false
          )
        ),
        snackbarType = SnackbarType.Toast
      )
    )
  }

  fun errorToast(
    @StringRes messageId: Int,
    toastId: String = nextToastId(),
    screenKey: ScreenKey = MainScreen.SCREEN_KEY,
    duration: Duration = STANDARD_DELAY.milliseconds
  ) {
    errorToast(
      message = appContext.getString(messageId),
      toastId = toastId,
      screenKey = screenKey,
      duration = duration
    )
  }

  fun errorToast(
    message: String,
    toastId: String = nextToastId(),
    screenKey: ScreenKey = MainScreen.SCREEN_KEY,
    duration: Duration = STANDARD_DELAY.milliseconds
  ) {
    pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.Toast(toastId),
        aliveUntil = SnackbarInfo.snackbarDuration(duration),
        screenKey = screenKey,
        content = listOf(
          SnackbarContentItem.Text(
            text = message,
            takeWholeWidth = false
          )
        ),
        snackbarType = SnackbarType.ErrorToast
      )
    )
  }

  fun pushSnackbar(snackbarInfo: SnackbarInfo) {
    _snackbarEventFlow.tryEmit(SnackbarInfoEvent.Push(snackbarInfo))
  }

  fun popSnackbar(id: SnackbarId) {
    _snackbarEventFlow.tryEmit(SnackbarInfoEvent.Pop(id))
  }

  fun pushThreadNewPostsSnackbar(
    newPostsCount: Int,
    updatedPostsCount: Int,
    deletedPostsCount: Int,
    screenKey: ScreenKey
  ) {
    if (newPostsCount <= 0 && updatedPostsCount <= 0 && deletedPostsCount <= 0) {
      return
    }

    val newPostsMessage = buildString {
      if (newPostsCount > 0) {
        append(
          appContext.resources.getQuantityString(
            R.plurals.new_posts_with_number,
            newPostsCount,
            newPostsCount
          )
        )
      }

      if (updatedPostsCount > 0) {
        if (isNotEmpty()) {
          append(", ")
        }

        append(
          appContext.resources.getQuantityString(
            R.plurals.updated_posts_with_number,
            updatedPostsCount,
            updatedPostsCount
          )
        )
      }

      if (deletedPostsCount > 0) {
        if (isNotEmpty()) {
          append(", ")
        }

        append(
          appContext.resources.getQuantityString(
            R.plurals.deleted_posts_with_number,
            deletedPostsCount,
            deletedPostsCount
          )
        )
      }
    }

    if (newPostsMessage.isEmpty()) {
      return
    }

    pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.NewPosts,
        aliveUntil = standardAliveUntil(),
        screenKey = screenKey,
        content = listOf(SnackbarContentItem.Text(newPostsMessage))
      )
    )
  }

  fun popThreadNewPostsSnackbar() {
    popSnackbar(SnackbarId.NewPosts)
  }

  fun pushCatalogOrThreadPostsLoadingSnackbar(postsCount: Int, screenKey: ScreenKey) {
    if (postsCount <= 0) {
      return
    }

    val processingPostsMessage = appContext.resources.getQuantityString(
      R.plurals.processing_posts_with_number,
      postsCount,
      postsCount
    )

    pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.CatalogOrThreadPostsLoading,
        aliveUntil = null,
        screenKey = screenKey,
        content = listOf(
          SnackbarContentItem.LoadingIndicator,
          SnackbarContentItem.Spacer(8.dp),
          SnackbarContentItem.Text(processingPostsMessage)
        )
      )
    )
  }

  fun popCatalogOrThreadPostsLoadingSnackbar() {
    popSnackbar(SnackbarId.CatalogOrThreadPostsLoading)
  }

  private fun standardAliveUntil() = SystemClock.elapsedRealtime() + STANDARD_DELAY

  class RemovedSnackbarInfo(
    val snackbarId: SnackbarId,
    val timedOut: Boolean
  )

  companion object {
    private val TOAST_ID_COUNTER = AtomicLong(0L)
    private const val STANDARD_DELAY = 3000

    fun nextToastId(): String = "toast_${TOAST_ID_COUNTER.getAndIncrement()}"
  }

}