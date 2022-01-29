package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference

class HomeScreenViewModel(
  private val siteManager: SiteManager
) : BaseViewModel() {
  private val currentPageValue = AtomicReference<CurrentPage?>()
  private val _currentPageFlow = MutableSharedFlow<CurrentPage>(extraBufferCapacity = Channel.UNLIMITED)

  val currentPageFlow: SharedFlow<CurrentPage>
    get() = _currentPageFlow.asSharedFlow()
  val currentPage: CurrentPage?
    get() = currentPageValue.get()

  fun updateCurrentPage(
    screenKey: ScreenKey,
    animate: Boolean = true,
    notifyListeners: Boolean = true
  ) {
    if (screenKey == currentPage?.screenKey) {
      return
    }

    val newCurrentPage = CurrentPage(screenKey, animate)
    currentPageValue.set(newCurrentPage)

    if (notifyListeners) {
      _currentPageFlow.tryEmit(newCurrentPage)
    }
  }

  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    return siteManager.resolveDescriptorFromRawIdentifier(rawIdentifier)
  }

  fun toast(message: String, duration: Int = 2000) {
    // TODO(KurobaEx):
  }

  data class CurrentPage(
    val screenKey: ScreenKey,
    val animate: Boolean = false
  )

}