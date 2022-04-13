package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.NavigationElement
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NavigationHistoryManager {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val navigationHistory = mutableListWithCap<NavigationElement>(128)

  private val _navigationUpdates = MutableSharedFlow<NavigationUpdate>(extraBufferCapacity = Channel.UNLIMITED)
  val navigationUpdates: SharedFlow<NavigationUpdate>
    get() = _navigationUpdates.asSharedFlow()

  suspend fun all(): List<NavigationElement> {
    return mutex.withLock { navigationHistory.toList() }
  }

  suspend fun load(history: List<NavigationElement>) {
    if (history.isEmpty()) {
      return
    }

    mutex.withLock {
      navigationHistory.clear()
      navigationHistory.addAll(history)

      _navigationUpdates.emit(NavigationUpdate.Loaded(history))
    }
  }

  suspend fun addOrReorder(navigationElement: NavigationElement) {
    mutex.withLock {
      val indexOfExisting = navigationHistory
        .indexOfFirst { existingNavigationElement -> existingNavigationElement == navigationElement  }

      when {
        indexOfExisting < 0 -> {
          navigationHistory.add(0, navigationElement)
          _navigationUpdates.emit(NavigationUpdate.Added(navigationElement))
        }
        indexOfExisting > 0 -> {
          navigationHistory.add(0, navigationHistory.removeAt(indexOfExisting))
          _navigationUpdates.emit(NavigationUpdate.Moved(indexOfExisting, navigationElement))
        }
        else -> {
          // Already at 0th index, nothing to do
        }
      }
    }
  }

  suspend fun moveToTop(chanDescriptor: ChanDescriptor) {
    mutex.withLock {
      val indexOfExisting = navigationHistory
        .indexOfFirst { existingNavigationElement -> existingNavigationElement.chanDescriptor == chanDescriptor }

      if (indexOfExisting > 0) {
        val removedElement = navigationHistory.removeAt(indexOfExisting)
        navigationHistory.add(0, removedElement)

        _navigationUpdates.emit(NavigationUpdate.Moved(indexOfExisting, removedElement))
      }
    }
  }

  suspend fun remove(chanDescriptor: ChanDescriptor) {
    mutex.withLock {
      val indexOfExisting = navigationHistory
        .indexOfFirst { existingNavigationElement -> existingNavigationElement.chanDescriptor == chanDescriptor }

      if (indexOfExisting >= 0) {
        val removedElement = navigationHistory.removeAt(indexOfExisting)
        _navigationUpdates.emit(NavigationUpdate.Removed(removedElement))
      }
    }
  }

}

sealed class NavigationUpdate {
  data class Loaded(val navigationElements: List<NavigationElement>) : NavigationUpdate()

  data class Added(val navigationElement: NavigationElement) : NavigationUpdate()
  data class Removed(val navigationElement: NavigationElement) : NavigationUpdate()
  data class Moved(val prevIndex: Int, val navigationElement: NavigationElement) : NavigationUpdate()
}