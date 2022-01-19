package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.ScreenKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class HomeScreenViewModel : BaseViewModel() {
  private val _currentPage = MutableSharedFlow<CurrentPage>(
    replay = 1,
    extraBufferCapacity = Channel.UNLIMITED
  )

  val currentPage: SharedFlow<CurrentPage>
    get() = _currentPage.asSharedFlow()

  fun updateCurrentPage(screenKey: ScreenKey, animate: Boolean) {
    _currentPage.tryEmit(CurrentPage(screenKey, animate))
  }

  data class CurrentPage(
    val screenKey: ScreenKey,
    val animate: Boolean = false
  )

}