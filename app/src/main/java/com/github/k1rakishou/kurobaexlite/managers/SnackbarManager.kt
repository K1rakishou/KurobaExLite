package com.github.k1rakishou.kurobaexlite.managers

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarContentItem
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarId
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfo
import com.github.k1rakishou.kurobaexlite.ui.elements.snackbar.SnackbarInfoEvent
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.screens.main.MainScreen
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

class SnackbarManager(
  private val appContext: Context
) {
  private val _snackbarEventFlow = MutableSharedFlow<SnackbarInfoEvent>(extraBufferCapacity = Channel.UNLIMITED)
  val snackbarEventFlow: SharedFlow<SnackbarInfoEvent>
    get() = _snackbarEventFlow.asSharedFlow()

  private val activeSnackbarsFlow = MutableSharedFlow<Map<ScreenKey, List<SnackbarInfo>>>(extraBufferCapacity = Channel.UNLIMITED)

  fun onSnackbarsUpdated(snackbarsByScreenKey: Map<ScreenKey, List<SnackbarInfo>>) {
    activeSnackbarsFlow.tryEmit(snackbarsByScreenKey)
  }

  fun listenForActiveSnackbarsFlow(forScreenKey: ScreenKey): Flow<List<SnackbarInfo>> {
    return activeSnackbarsFlow.map { map ->
      val thisScreenSnackbars = map[forScreenKey] ?: emptyList()
      val mainScreenSnackbars = map[MainScreen.SCREEN_KEY] ?: emptyList()

      return@map thisScreenSnackbars + mainScreenSnackbars
    }
  }

  // A toast is a snack too
  fun toast(
    @StringRes messageId: Int,
    toastId: String = nextToastId(),
    screenKey: ScreenKey = MainScreen.SCREEN_KEY,
    duration: Int = STANDARD_DELAY
  ) {
    pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.Toast(toastId),
        aliveUntil = SystemClock.elapsedRealtime() + duration,
        screenKey = screenKey,
        content = listOf(SnackbarContentItem.Text(appContext.getString(messageId)))
      )
    )
  }

  fun toast(
    message: String,
    toastId: String = nextToastId(),
    screenKey: ScreenKey = MainScreen.SCREEN_KEY,
    duration: Int = STANDARD_DELAY
  ) {
    pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.Toast(toastId),
        aliveUntil = SystemClock.elapsedRealtime() + duration,
        screenKey = screenKey,
        content = listOf(SnackbarContentItem.Text(message))
      )
    )
  }

  fun popCatalogOrThreadPostsLoadingSnackbar() {
    popSnackbar(SnackbarId.CatalogOrThreadPostsLoading)
  }

  fun pushSnackbar(snackbarInfo: SnackbarInfo) {
    _snackbarEventFlow.tryEmit(SnackbarInfoEvent.Push(snackbarInfo))
  }

  fun popSnackbar(id: SnackbarId) {
    _snackbarEventFlow.tryEmit(SnackbarInfoEvent.Pop(id))
  }

  fun pushThreadNewPostsSnackbar(newPostsCount: Int, screenKey: ScreenKey) {
    if (newPostsCount <= 0) {
      return
    }

    val newPostsMessage = appContext.resources.getQuantityString(
      R.plurals.new_posts_with_number,
      newPostsCount,
      newPostsCount
    )

    pushSnackbar(
      SnackbarInfo(
        snackbarId = SnackbarId.NewPosts,
        aliveUntil = standardAliveUntil(),
        screenKey = screenKey,
        content = listOf(SnackbarContentItem.Text(newPostsMessage))
      )
    )
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

  private fun standardAliveUntil() = SystemClock.elapsedRealtime() + STANDARD_DELAY

  companion object {
    private val TOAST_ID_COUNTER = AtomicLong(0L)
    private const val STANDARD_DELAY = 2000

    fun nextToastId(): String = "toast_${TOAST_ID_COUNTER.getAndIncrement()}"
  }

}