package com.github.k1rakishou.kurobaexlite.ui.screens.home

import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseAndroidViewModel
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.base.ScreenKey
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.logcat

class HomeScreenViewModel(
  application: KurobaExLiteApplication,
  private val siteManager: SiteManager
) : BaseAndroidViewModel(application) {
  private val toolbarHeight by lazy { resources.getDimension(R.dimen.toolbar_height) }
  private val currentPageValue = AtomicReference<CurrentPage?>()
  val toolbarVisibilityInfo = ToolbarVisibilityInfo()

  private val _drawerVisibilityFlow = MutableStateFlow<DrawerVisibility>(DrawerVisibility.Closed)
  val drawerVisibilityFlow: StateFlow<DrawerVisibility>
    get() = _drawerVisibilityFlow.asStateFlow()

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

    toolbarVisibilityInfo.update(postListScrollState = 1f)
  }

  fun resolveDescriptorFromRawIdentifier(rawIdentifier: String): ResolvedDescriptor? {
    return siteManager.resolveDescriptorFromRawIdentifier(rawIdentifier)
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

  fun onPostListTouchingTopOrBottomStateChanged(touching: Boolean) {
    toolbarVisibilityInfo.update(postListTouchingTopOrBottomState = touching)
  }

  fun onChildScreenSearchStateChanged(screenKey: ScreenKey, searchQuery: String?) {
    val childScreenSearchInfo = ChildScreenSearchInfo(
      screenKey = screenKey,
      usingSearch = searchQuery != null
    )

    toolbarVisibilityInfo.update(childScreenSearchInfo = childScreenSearchInfo)
  }

  fun isDrawerOpenedOrOpening(): Boolean {
    return when (_drawerVisibilityFlow.value) {
      is DrawerVisibility.Drag -> true
      DrawerVisibility.Closed,
      DrawerVisibility.Closing -> false
      DrawerVisibility.Opened,
      DrawerVisibility.Opening -> true
    }
  }

  fun isDrawerFullyOpened(): Boolean {
    return _drawerVisibilityFlow.value is DrawerVisibility.Opened
  }

  fun openDrawer(withAnimation: Boolean = true) {
    if (withAnimation) {
      _drawerVisibilityFlow.value = DrawerVisibility.Opening
    } else {
      _drawerVisibilityFlow.value = DrawerVisibility.Opened
    }
  }

  fun closeDrawer(withAnimation: Boolean = true) {
    if (withAnimation) {
      _drawerVisibilityFlow.value = DrawerVisibility.Closing
    } else {
      _drawerVisibilityFlow.value = DrawerVisibility.Closed
    }
  }

  fun dragDrawer(isDragging: Boolean, progress: Float, velocity: Float) {
    _drawerVisibilityFlow.value = DrawerVisibility.Drag(isDragging, progress, velocity)
  }

  fun onFabClicked(screenKey: ScreenKey) {
    logcat { "onFabClicked($screenKey)" }
  }

  sealed class DrawerVisibility {
    data class Drag(
      val isDragging: Boolean,
      val progress: Float,
      val velocity: Float
    ) : DrawerVisibility() {
      val progressInverted: Float
        get() = 1f - progress

      override fun toString(): String = "DrawerVisibility.Drag($isDragging, $progress, $velocity)"
    }

    object Opening : DrawerVisibility() {
      override fun toString(): String = "DrawerVisibility.Opening()"
    }
    object Opened : DrawerVisibility() {
      override fun toString(): String = "DrawerVisibility.Opened()"
    }
    object Closing : DrawerVisibility() {
      override fun toString(): String = "DrawerVisibility.Closing()"
    }
    object Closed : DrawerVisibility() {
      override fun toString(): String = "DrawerVisibility.Closed()"
    }
  }

  data class ChildScreenSearchInfo(
    val screenKey: ScreenKey,
    val usingSearch: Boolean
  )

  data class CurrentPage(
    val screenKey: ScreenKey,
    val animate: Boolean = false
  )

  class ToolbarVisibilityInfo {
    private val _postListScrollState = MutableStateFlow<Float>(1f)
    val postListScrollState: StateFlow<Float>
      get() = _postListScrollState.asStateFlow()

    private val _postListTouchingTopOrBottomState = MutableStateFlow<Boolean>(false)
    val postListTouchingTopOrBottomState: StateFlow<Boolean>
      get() = _postListTouchingTopOrBottomState.asStateFlow()

    private val _postListDragState = MutableStateFlow(false)
    val postListDragState: StateFlow<Boolean>
      get() = _postListDragState.asStateFlow()

    private val _fastScrollerDragState = MutableStateFlow(false)
    val fastScrollerDragState: StateFlow<Boolean>
      get() = _fastScrollerDragState.asStateFlow()

    private val _childScreensUsingSearch = MutableStateFlow(mutableSetOf<ScreenKey>())
    val childScreensUsingSearch: StateFlow<Set<ScreenKey>>
      get() = _childScreensUsingSearch.asStateFlow()

    fun update(
      postListScrollState: Float? = null,
      postListTouchingTopOrBottomState: Boolean? = null,
      postListDragState: Boolean? = null,
      fastScrollerDragState: Boolean? = null,
      childScreenSearchInfo: ChildScreenSearchInfo? = null
    ) {
      postListScrollState?.let { _postListScrollState.value = it }
      postListTouchingTopOrBottomState?.let { _postListTouchingTopOrBottomState.value = it }
      postListDragState?.let { _postListDragState.value = it }
      fastScrollerDragState?.let { _fastScrollerDragState.value = it }

      childScreenSearchInfo?.let { screenSearchInfo ->
        val prevValue = _childScreensUsingSearch.value

        if (screenSearchInfo.usingSearch) {
          prevValue += screenSearchInfo.screenKey
        } else {
          prevValue -= screenSearchInfo.screenKey
        }

        _childScreensUsingSearch.value = prevValue
      }
    }

  }

}