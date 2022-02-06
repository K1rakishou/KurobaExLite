package com.github.k1rakishou.kurobaexlite.ui.screens.posts

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicReference

class HomeScreenViewModel(
  application: KurobaExLiteApplication,
  private val siteManager: SiteManager
) : BaseAndroidViewModel(application) {
  private val toolbarHeight by lazy { resources.getDimension(R.dimen.toolbar_height) }
  private val currentPageValue = AtomicReference<CurrentPage?>()

  private val _currentPageFlow = MutableSharedFlow<CurrentPage>(extraBufferCapacity = Channel.UNLIMITED)

  val currentPageFlow: SharedFlow<CurrentPage>
    get() = _currentPageFlow.asSharedFlow()
  val currentPage: CurrentPage?
    get() = currentPageValue.get()

  val toolbarVisibilityInfo = ToolbarVisibilityInfo()

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

    toolbarVisibilityInfo.update(postListScrollState = 1f)
  }

  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    return siteManager.resolveDescriptorFromRawIdentifier(rawIdentifier)
  }

  fun toast(message: String, duration: Int = 2000) {
    // TODO(KurobaEx):
  }

  fun onChildContentScrolling(delta: Float) {
    var currentTransparency = toolbarVisibilityInfo.postListScrollState.value
    currentTransparency += (delta / toolbarHeight)

    if (currentTransparency < 0f) {
      currentTransparency = 0f
    }

    if (currentTransparency > 1f) {
      currentTransparency = 1f
    }

    toolbarVisibilityInfo.update(postListScrollState = currentTransparency)
  }

  fun onPostListDragStateChanged(dragging: Boolean) {
    toolbarVisibilityInfo.update(postListDragState = dragging)
  }

  fun onFastScrollerDragStateChanged(dragging: Boolean) {
    toolbarVisibilityInfo.update(fastScrollerDragState = dragging)
  }

  fun onPostListTouchingBottomStateChanged(touchingBottom: Boolean) {
    toolbarVisibilityInfo.update(postListTouchingBottomState = touchingBottom)
  }

  data class CurrentPage(
    val screenKey: ScreenKey,
    val animate: Boolean = false
  )

  class ToolbarVisibilityInfo {
    private var _postListScrollState = MutableStateFlow<Float>(1f)
    val postListScrollState: StateFlow<Float>
      get() = _postListScrollState.asStateFlow()

    private var _postListTouchingBottomState = MutableStateFlow<Boolean>(false)
    val postListTouchingBottomState: StateFlow<Boolean>
      get() = _postListTouchingBottomState.asStateFlow()

    private var _postListDragState = MutableStateFlow(false)
    val postListDragState: StateFlow<Boolean>
      get() = _postListDragState.asStateFlow()

    private var _fastScrollerDragState = MutableStateFlow(false)
    val fastScrollerDragState: StateFlow<Boolean>
      get() = _fastScrollerDragState.asStateFlow()

    fun update(
      postListScrollState: Float? = null,
      postListTouchingBottomState: Boolean? = null,
      postListDragState: Boolean? = null,
      fastScrollerDragState: Boolean? = null
    ) {
      postListScrollState?.let { _postListScrollState.value = it }
      postListTouchingBottomState?.let { _postListTouchingBottomState.value = it }
      postListDragState?.let { _postListDragState.value = it }
      fastScrollerDragState?.let { _fastScrollerDragState.value = it }
    }

  }

}