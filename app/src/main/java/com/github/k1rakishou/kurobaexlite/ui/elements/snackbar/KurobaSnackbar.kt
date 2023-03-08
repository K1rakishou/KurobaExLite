package com.github.k1rakishou.kurobaexlite.ui.elements.snackbar

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.helpers.util.iteration
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import org.koin.core.context.GlobalContext
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration


@Composable
fun rememberKurobaSnackbarState(
  keyTag: String? = null
): KurobaSnackbarState {
  return remember {
    KurobaSnackbarState(tag = keyTag)
  }
}

@Stable
class KurobaSnackbarState(
  private val tag: String?
) {
  private val snackbarManager: SnackbarManager by lazy { GlobalContext.get().get() }

  private val _activeSnackbars = mutableStateListOf<SnackbarInfo>()
  val activeSnackbars: List<SnackbarInfo>
    get() = _activeSnackbars
  
  private val _snackbarAnimations = mutableStateMapOf<Long, SnackbarAnimation>()
  val snackbarAnimations: Map<Long, SnackbarAnimation>
    get() = _snackbarAnimations

  private var isSnackbarVisible = false

  fun pushSnackbar(snackbarInfo: SnackbarInfo) {
    val indexOfSnackbar = _activeSnackbars
      .indexOfFirst { info -> info.snackbarId == snackbarInfo.snackbarId }

    if (indexOfSnackbar < 0) {
      Snapshot.withMutableSnapshot {
        _activeSnackbars.add(snackbarInfo)

        val snackbarIdForCompose = snackbarInfo.snackbarIdForCompose
        _snackbarAnimations[snackbarIdForCompose] = SnackbarAnimation.Push(snackbarIdForCompose)
        isSnackbarVisible = true
      }
      
      updateSnackbarsCount()
    } else {
      val prevSnackbarInfo = _activeSnackbars[indexOfSnackbar]
      
      if (prevSnackbarInfo != snackbarInfo || !prevSnackbarInfo.contentsEqual(snackbarInfo)) {
        val snackbarIdForCompose = snackbarInfo.snackbarIdForCompose

        _activeSnackbars[indexOfSnackbar] = snackbarInfo

        if (!isSnackbarVisible) {
          _snackbarAnimations[snackbarIdForCompose] = SnackbarAnimation.Push(snackbarIdForCompose)
          isSnackbarVisible = true
        }
      }
    }
  }

  fun popSnackbar(id: SnackbarId) {
    val indexOfSnackbar = _activeSnackbars
      .indexOfFirst { snackbarInfo -> snackbarInfo.snackbarId == id }

    if (indexOfSnackbar >= 0) {
      val snackbarInfo = _activeSnackbars.getOrNull(indexOfSnackbar) 
        ?: return

      val snackbarIdForCompose = snackbarInfo.snackbarIdForCompose
      _snackbarAnimations[snackbarIdForCompose] = SnackbarAnimation.Pop(snackbarIdForCompose)
      isSnackbarVisible = false
    }
  }
  
  fun onAnimationEnded(snackbarAnimation: SnackbarAnimation) {
    when (snackbarAnimation) {
      is SnackbarAnimation.Push -> {
        // no-op
      }
      is SnackbarAnimation.Pop -> {
        val indexOfSnackbar = _activeSnackbars.indexOfFirst { snackbarInfo -> 
          snackbarInfo.snackbarIdForCompose == snackbarAnimation.snackbarId 
        }
        
        if (indexOfSnackbar >= 0) {
          val removedSnackbar = _activeSnackbars.removeAt(indexOfSnackbar)
          val removedSnackbarInfo = SnackbarManager.RemovedSnackbarInfo(removedSnackbar.snackbarId, false)
          onSnackbarsRemoved(setOf(removedSnackbarInfo))
          updateSnackbarsCount()
        }
      }
    }

    _snackbarAnimations.remove(snackbarAnimation.snackbarId)
  }

  fun removeOldSnackbars() {
    val currentTime = SystemClock.elapsedRealtime()
    var currentSnackbarsCount = _activeSnackbars.size
    val activeSnackbarsCopy = _activeSnackbars.toList()

    activeSnackbarsCopy.iteration { _, activeSnackbar ->
      if (activeSnackbar.aliveUntil != null && currentTime >= activeSnackbar.aliveUntil) {
        val removedSnackbar = SnackbarManager.RemovedSnackbarInfo(activeSnackbar.snackbarId, true)
        popSnackbar(removedSnackbar.snackbarId)

        --currentSnackbarsCount
      }

      return@iteration true
    }
  }

  fun removeSnackbarsExceedingAvailableHeight(
    visibleSnackbarSizeMap: Map<SnackbarId, IntSize>,
    maxAvailableHeight: Int
  ) {
    val activeSnackbarsCount = _activeSnackbars.size

    var totalTakenHeight = visibleSnackbarSizeMap.values
      .sumOf { intSize -> intSize.height }

    for (index in 0 until activeSnackbarsCount) {
      if (totalTakenHeight < maxAvailableHeight) {
        return
      }

      val snackbarToRemove = _activeSnackbars.getOrNull(index)
      if (snackbarToRemove == null) {
        return
      }

      val snackbarHeight = visibleSnackbarSizeMap[snackbarToRemove.snackbarId]?.height
        ?: continue

      val removedSnackbar = SnackbarManager.RemovedSnackbarInfo(snackbarToRemove.snackbarId, true)
      popSnackbar(removedSnackbar.snackbarId)

      totalTakenHeight -= snackbarHeight
    }
  }

  private fun updateSnackbarsCount() {
    val snackbarsByScreenKey = _activeSnackbars.groupBy { it.screenKey }
    snackbarManager.onSnackbarsUpdated(snackbarsByScreenKey)
  }

  private fun onSnackbarsRemoved(removedSnackbars: Set<SnackbarManager.RemovedSnackbarInfo>) {
    snackbarManager.onSnackbarsRemoved(removedSnackbars)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KurobaSnackbarState

    if (tag != other.tag) return false

    return true
  }

  override fun hashCode(): Int {
    return tag?.hashCode() ?: 0
  }

}

@Immutable
sealed class SnackbarAnimation {
  abstract val snackbarId: Long
  
  data class Push(
    override val snackbarId: Long
  ) : SnackbarAnimation()

  data class Pop(
    override val snackbarId: Long
  ) : SnackbarAnimation()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SnackbarAnimation

    if (snackbarId != other.snackbarId) return false

    return true
  }

  override fun hashCode(): Int {
    return snackbarId.hashCode()
  }

}

sealed class SnackbarInfoEvent {
  data class Push(val snackbarInfo: SnackbarInfo) : SnackbarInfoEvent()
  data class Pop(val id: SnackbarId) : SnackbarInfoEvent()
}

@Stable
class SnackbarInfo(
  val snackbarId: SnackbarId,
  val createdAt: Long = SystemClock.elapsedRealtime(),
  val aliveUntil: Long?,
  val screenKey: ScreenKey = MainScreen.SCREEN_KEY,
  val content: List<SnackbarContentItem>,
  val snackbarType: SnackbarType = SnackbarType.Default
) {
  val snackbarIdForCompose: Long = nextSnackbarIdForCompose()

  val hasClickableItems: Boolean
    get() = content.any { contentItem -> contentItem is SnackbarClickable }
  
  fun contentsEqual(other: SnackbarInfo): Boolean {
    return content == other.content
  }
  
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SnackbarInfo

    if (snackbarId != other.snackbarId) return false
    if (createdAt != other.createdAt) return false
    if (aliveUntil != other.aliveUntil) return false
    if (screenKey != other.screenKey) return false
    if (snackbarType != other.snackbarType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = snackbarId.hashCode()
    result = 31 * result + createdAt.hashCode()
    result = 31 * result + (aliveUntil?.hashCode() ?: 0)
    result = 31 * result + screenKey.hashCode()
    result = 31 * result + snackbarType.hashCode()
    return result
  }

  companion object {
    private val snackbarIdsForCompose = AtomicLong(0)

    fun nextSnackbarIdForCompose(): Long = snackbarIdsForCompose.incrementAndGet()

    fun snackbarDuration(duration: Duration): Long {
      return SystemClock.elapsedRealtime() + duration.inWholeMilliseconds
    }
  }

}

sealed class SnackbarType {
  val isToast: Boolean
    get() = this is Toast || this is ErrorToast

  object Default : SnackbarType()
  object Toast : SnackbarType()
  object ErrorToast : SnackbarType()
}

interface SnackbarClickable {
  val key: Any
  val data: Any?
}

@Stable
sealed class SnackbarContentItem {
  object LoadingIndicator : SnackbarContentItem()

  data class Text(
    private val text: String,
    val textColor: Color? = null,
    val takeWholeWidth: Boolean = true
  ) : SnackbarContentItem() {
    val formattedText by lazy { text.replace('\n', ' ') }
  }

  data class Button(
    override val key: Any,
    override val data: Any? = null,
    private val text: String,
    val textColor: Color? = null,
  ) : SnackbarContentItem(), SnackbarClickable {
    val formattedText by lazy { text.uppercase(Locale.ENGLISH) }
  }

  data class Icon(
    @DrawableRes val drawableId: Int,
  ) : SnackbarContentItem()

  data class Spacer(val space: Dp) : SnackbarContentItem()
}

sealed class SnackbarId(
  val id: String
) {
  object CatalogOrThreadPostsLoading : SnackbarId("catalog_or_thread_posts_loading")
  object NewPosts : SnackbarId("new_posts")
  object NavHistoryElementRemoved : SnackbarId("nav_history_element_removed")
  object ThreadBookmarkRemoved : SnackbarId("thread_bookmark_removed")
  object ReloadLastVisitedCatalog : SnackbarId("reload_last_visited_catalog")
  object ReloadLastVisitedThread : SnackbarId("reload_last_visited_thread")

  class Toast(toastId: String) : SnackbarId(toastId)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SnackbarId

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun toString(): String {
    if (this is Toast) {
      return "Toast(${id})"
    }

    return "Snackbar(${id})"
  }

}