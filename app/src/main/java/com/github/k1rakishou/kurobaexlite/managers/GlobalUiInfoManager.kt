package com.github.k1rakishou.kurobaexlite.managers

import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreen
import com.github.k1rakishou.kurobaexlite.features.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.features.reply.ReplyLayoutVisibility
import com.github.k1rakishou.kurobaexlite.helpers.SaveableComponent
import com.github.k1rakishou.kurobaexlite.helpers.resource.IAppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.LayoutType
import com.github.k1rakishou.kurobaexlite.helpers.settings.PostViewMode
import com.github.k1rakishou.kurobaexlite.helpers.util.getParcelableMap
import com.github.k1rakishou.kurobaexlite.helpers.util.getSerializableMap
import com.github.k1rakishou.kurobaexlite.helpers.util.putParcelableMap
import com.github.k1rakishou.kurobaexlite.helpers.util.putSerializableMap
import com.github.k1rakishou.kurobaexlite.model.data.ui.ChildScreenSearchInfo
import com.github.k1rakishou.kurobaexlite.model.data.ui.CurrentPage
import com.github.k1rakishou.kurobaexlite.model.data.ui.DrawerVisibility
import com.github.k1rakishou.kurobaexlite.model.data.ui.HideableUiVisibilityInfo
import com.github.k1rakishou.kurobaexlite.ui.helpers.WindowSizeClass
import com.github.k1rakishou.kurobaexlite.ui.helpers.WindowWidthSizeClass
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.logcat

class GlobalUiInfoManager(
  appScope: CoroutineScope,
  private val appResources: IAppResources,
  private val appSettings: AppSettings
) : SaveableComponent {
  private val job = SupervisorJob()
  private val coroutineScope = CoroutineScope(Dispatchers.Main + job + appScope.coroutineContext)

  val toolbarHeight by lazy { appResources.dimension(R.dimen.toolbar_height) }
  val isTablet by lazy { appResources.boolean(R.bool.isTablet) }

  private val _lastTouchPosition = Point(0, 0)
  val lastTouchPosition: Point
    get() = Point(_lastTouchPosition.x, _lastTouchPosition.y)

  val orientations = arrayOf(
    Configuration.ORIENTATION_UNDEFINED,
    Configuration.ORIENTATION_PORTRAIT,
    Configuration.ORIENTATION_LANDSCAPE
  )

  val defaultHorizPadding by lazy { if (isTablet) 12.dp else 8.dp }
  val defaultVertPadding by lazy { if (isTablet) 10.dp else 6.dp }

  private val hideableUiVisibilityInfoMap = mutableMapOf<ScreenKey, HideableUiVisibilityInfo>()
  private val replyLayoutVisibilityInfoMap = mutableMapOf<ScreenKey, MutableState<ReplyLayoutVisibility>>()
  private val postSelectionInfoMap = mutableMapOf<ScreenKey, MutableState<Boolean>>()

  private val pagerDisplayedDeferred = CompletableDeferred<Unit>()
  private val currentUiLayoutModeKnownDeferred = CompletableDeferred<Unit>()
  private val currentUiLayoutModeState = MutableStateFlow<MainUiLayoutMode>(MainUiLayoutMode.Phone)

  private val _notEnoughWidthForSplitLayoutFlow = MutableSharedFlow<WindowWidthSizeClass?>(Channel.RENDEZVOUS)
  val notEnoughWidthForSplitLayoutFlow: SharedFlow<WindowWidthSizeClass?>
    get() = _notEnoughWidthForSplitLayoutFlow.asSharedFlow()

  private var _totalScreenWidthState = MutableStateFlow(0)
  val totalScreenWidthState: StateFlow<Int>
    get() = _totalScreenWidthState.asStateFlow()

  private var _totalScreenHeightState = MutableStateFlow(0)
  val totalScreenHeightState: StateFlow<Int>
    get() = _totalScreenHeightState.asStateFlow()

  private var _currentDrawerVisibility: DrawerVisibility = DrawerVisibility.Closed
  val currentDrawerVisibility: DrawerVisibility
    get() = _currentDrawerVisibility

  private val _drawerVisibilityFlow = MutableSharedFlow<DrawerVisibility>(Channel.UNLIMITED)
  val drawerVisibilityFlow: SharedFlow<DrawerVisibility>
    get() = _drawerVisibilityFlow.asSharedFlow()

  private val _currentPageMapFlow = mutableMapOf<MainUiLayoutMode, MutableStateFlow<CurrentPage>>()

  // vvv Settings
  private val _globalFontSizeMultiplier = MutableStateFlow(100)
  val globalFontSizeMultiplier: StateFlow<Int>
    get() = _globalFontSizeMultiplier.asStateFlow()

  private val _catalogPostViewMode = MutableStateFlow<PostViewMode>(PostViewMode.List)
  val catalogPostViewMode: StateFlow<PostViewMode>
    get() = _catalogPostViewMode.asStateFlow()
  private val _catalogGridModeColumnCount = MutableStateFlow<Int>(2)
  val catalogGridModeColumnCount: StateFlow<Int>
    get() = _catalogGridModeColumnCount.asStateFlow()
  private val _albumGridModeColumnCount = MutableStateFlow<Int>(3)
  val albumGridModeColumnCount: StateFlow<Int>
    get() = _albumGridModeColumnCount.asStateFlow()
  private val _albumShowImageInfo = MutableStateFlow<Boolean>(true)
  val albumShowImageInfo: StateFlow<Boolean>
    get() = _albumShowImageInfo.asStateFlow()

  private val _historyEnabled = MutableStateFlow(true)
  val historyEnabled: StateFlow<Boolean>
    get() = _historyEnabled.asStateFlow()
  // ^^^ Settings

  override val saveableComponentKey: String = SAVE_STATE_KEY

  override fun saveState(): Bundle {
    return Bundle().apply {
      putParcelableMap(TOOLBAR_VISIBILITY_INFO_MAP, hideableUiVisibilityInfoMap)

      putSerializableMap(
        key = CURRENT_PAGE_MAP,
        map = _currentPageMapFlow,
        mapper = { mainUiLayoutMode, currentPageStateFlow ->
          return@putSerializableMap Bundle().apply {
            putInt(MAIN_UI_LAYOUT_MODE, mainUiLayoutMode.value)
            putParcelable(CURRENT_PAGE, currentPageStateFlow.value)
          }
        }
      )

      putBoolean(IS_DRAWER_OPENED, _currentDrawerVisibility.isOpened)
    }
  }

  override fun restoreFromState(bundle: Bundle?) {
    if (bundle == null) {
      return
    }

    bundle.getParcelableMap<ScreenKey, HideableUiVisibilityInfo>(TOOLBAR_VISIBILITY_INFO_MAP)
      .let { restoredToolbarVisibilityMap ->
        hideableUiVisibilityInfoMap.clear()
        hideableUiVisibilityInfoMap.putAll(restoredToolbarVisibilityMap)
      }

    bundle.getSerializableMap(
      key = CURRENT_PAGE_MAP,
      mapper = { bundle ->
        val mainUiLayoutMode = MainUiLayoutMode.fromRawValue(bundle.getInt(MAIN_UI_LAYOUT_MODE, 0))
        val currentPage = bundle.getParcelable<CurrentPage>(CURRENT_PAGE)
          ?.let { page -> layoutModeDependantPage(mainUiLayoutMode, page) }

        return@getSerializableMap Pair(mainUiLayoutMode, currentPage)
      }
    ).let { resultMap ->
      _currentPageMapFlow.clear()

      resultMap.entries.forEach { (mainUiLayoutMode, currentPage) ->
        if (currentPage == null) {
          return@forEach
        }

        _currentPageMapFlow[mainUiLayoutMode] = MutableStateFlow(layoutModeDependantPage(mainUiLayoutMode, currentPage))
      }
    }

    bundle.getBoolean(IS_DRAWER_OPENED, false).let { isDrawerOpened ->
      val drawerVisibility = if (isDrawerOpened) {
        DrawerVisibility.Opened
      } else {
        DrawerVisibility.Closed
      }

      updateDrawerVisibility(drawerVisibility)
    }
  }

  fun init(): Boolean {
    runBlocking { initInternal() }
    return true
  }

  private suspend fun initInternal() {
    logcat { "UiInfoManager initialization started" }
    job.cancelChildren()

    _globalFontSizeMultiplier.value = appSettings.globalFontSizeMultiplier.read()
    _catalogPostViewMode.value = appSettings.catalogPostViewMode.read().toPostViewMode()
    _catalogGridModeColumnCount.value = appSettings.catalogGridModeColumnCount.read()
    _albumGridModeColumnCount.value = appSettings.albumColumnCount.read()
    _albumShowImageInfo.value = appSettings.albumShowImageInfo.read()
    _historyEnabled.value = appSettings.historyEnabled.read()

    coroutineScope.launch {
      appSettings.globalFontSizeMultiplier.listen()
        .collectLatest { value -> _globalFontSizeMultiplier.value = value }
    }

    coroutineScope.launch {
      appSettings.catalogPostViewMode.listen()
        .collectLatest { value -> _catalogPostViewMode.value = value.toPostViewMode() }
    }

    coroutineScope.launch {
      appSettings.catalogGridModeColumnCount.listen()
        .collectLatest { value -> _catalogGridModeColumnCount.value = value }
    }

    coroutineScope.launch {
      appSettings.albumColumnCount.listen()
        .collectLatest { value -> _albumGridModeColumnCount.value = value }
    }

    coroutineScope.launch {
      appSettings.albumShowImageInfo.listen()
        .collectLatest { value -> _albumShowImageInfo.value = value }
    }

    coroutineScope.launch {
      appSettings.historyEnabled.listen()
        .collectLatest { value -> _historyEnabled.value = value }
    }

    logcat { "UiInfoManager initialization finished" }
  }

  suspend fun waitUntilLayoutModeIsKnown() {
    if (currentUiLayoutModeKnownDeferred.isCompleted) {
      return
    }

    currentUiLayoutModeKnownDeferred.await()
  }

  suspend fun waitUntilHomeScreenPagerDisplayed() {
    if (pagerDisplayedDeferred.isCompleted) {
      return
    }

    pagerDisplayedDeferred.await()
  }

  fun onPagerDisplayed() {
    pagerDisplayedDeferred.complete(Unit)
  }

  fun currentPageFlow(uiLayoutMode: MainUiLayoutMode): StateFlow<CurrentPage> {
    return _currentPageMapFlow.getOrPut(
      key = uiLayoutMode,
      defaultValue = { defaultCurrentPageByUiLayoutMode(uiLayoutMode) }
    )
  }

  fun currentPage(): CurrentPage? {
    return _currentPageMapFlow[currentUiLayoutModeState.value]?.value
  }

  fun currentPage(uiLayoutMode: MainUiLayoutMode): CurrentPage? {
    return _currentPageMapFlow[uiLayoutMode]?.value
  }

  fun updateMaxParentSize(availableWidth: Int, availableHeight: Int) {
    val prevAvailableWidth = _totalScreenWidthState.value
    if (prevAvailableWidth != availableWidth) {
      _totalScreenWidthState.value = availableWidth
    }

    val prevAvailableHeight = _totalScreenHeightState.value
    if (prevAvailableHeight != availableHeight) {
      _totalScreenHeightState.value = availableHeight
    }
  }

  fun setLastTouchPosition(x: Float, y: Float) {
    _lastTouchPosition.set(x.toInt(), y.toInt())
  }

  fun isReplyLayoutOpened(screenKey: ScreenKey): Boolean {
    return replyLayoutVisibilityInfoMap.entries
      .any { (replyLayoutScreenKey, replyLayoutVisibilityState) ->
        return@any replyLayoutScreenKey == screenKey &&
          replyLayoutVisibilityState.value != ReplyLayoutVisibility.Collapsed
      }
  }

  fun isInPostSelectionMode(screenKey: ScreenKey): Boolean {
    return postSelectionInfoMap.entries
      .any { (otherScreenKey, isInPostSelectionMode) ->
        return@any otherScreenKey == screenKey && isInPostSelectionMode.value
      }
  }

  fun isAnyReplyLayoutOpened(): Boolean {
    return replyLayoutVisibilityInfoMap.values
      .any { replyLayoutVisibilityState -> replyLayoutVisibilityState.value != ReplyLayoutVisibility.Collapsed }
  }

  fun getOrCreateHideableUiVisibilityInfo(screenKey: ScreenKey): HideableUiVisibilityInfo {
    return hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )
  }

  fun replyLayoutVisibilityInfoStateForScreen(screenKey: ScreenKey): State<ReplyLayoutVisibility> {
    return replyLayoutVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { mutableStateOf(ReplyLayoutVisibility.Collapsed) }
    )
  }

  fun updateCurrentPageForLayoutMode(
    screenKey: ScreenKey,
    mainUiLayoutMode: MainUiLayoutMode
  ) {
    val currentUiLayoutMode = currentUiLayoutModeState.value

    if (currentUiLayoutMode != mainUiLayoutMode) {
      return
    }

    if (screenKey == currentPage(currentUiLayoutMode)?.screenKey) {
      return
    }

    val newCurrentPage = layoutModeDependantPage(
      mainUiLayoutMode = currentUiLayoutMode,
      currentPage = CurrentPage(screenKey, false)
    )

    _currentPageMapFlow[currentUiLayoutMode]?.tryEmit(newCurrentPage)

    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(contentListScrollState = 1f)
  }

  fun updateCurrentPage(
    screenKey: ScreenKey,
    animate: Boolean = true
  ) {
    val currentUiLayoutMode = currentUiLayoutModeState.value

    if (screenKey == currentPage(currentUiLayoutMode)?.screenKey) {
      return
    }

    val newCurrentPage = layoutModeDependantPage(
      mainUiLayoutMode = currentUiLayoutMode,
      currentPage = CurrentPage(screenKey, animate)
    )

    _currentPageMapFlow[currentUiLayoutMode]?.tryEmit(newCurrentPage)

    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(contentListScrollState = 1f)
  }

  fun onContentListScrolling(
    screenKey: ScreenKey,
    delta: Float
  ) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    var currentTransparency = hideableUiVisibilityInfo.contentListScrollState.value
    currentTransparency += (delta / toolbarHeight)

    if (currentTransparency < 0f) {
      currentTransparency = 0f
    }

    if (currentTransparency > 1f) {
      currentTransparency = 1f
    }

    hideableUiVisibilityInfo.update(contentListScrollState = currentTransparency)
  }

  fun onCurrentlyTouchingContentList(screenKey: ScreenKey, touching: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(contentListTouchingState = touching)
  }

  fun onFastScrollerDragStateChanged(screenKey: ScreenKey, dragging: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(fastScrollerDragState = dragging)
  }

  fun onContentListTouchingTopOrBottomStateChanged(screenKey: ScreenKey, touching: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(contentListTouchingTopOrBottomState = touching)
  }

  fun onChildScreenSearchStateChanged(screenKey: ScreenKey, usingSearch: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    val childScreenSearchInfo = ChildScreenSearchInfo(
      screenKey = screenKey,
      usingSearch = usingSearch
    )

    hideableUiVisibilityInfo.update(childScreenSearchInfo = childScreenSearchInfo)
  }

  fun replyLayoutVisibilityStateChanged(
    screenKey: ScreenKey,
    replyLayoutVisibility: ReplyLayoutVisibility
  ) {
    val replyLayoutVisibilityState = replyLayoutVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { mutableStateOf(replyLayoutVisibility) }
    )
    replyLayoutVisibilityState.value = replyLayoutVisibility

    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(replyLayoutOpened = replyLayoutVisibility != ReplyLayoutVisibility.Collapsed)
  }

  fun screenIsInPostSelectionModeStateChanged(
    screenKey: ScreenKey,
    isInPostSelectionMode: Boolean
  ) {
    val isInPostSelectionModeState = postSelectionInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { mutableStateOf(isInPostSelectionMode) }
    )

    isInPostSelectionModeState.value = isInPostSelectionMode

    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(isInPostSelectionMode = isInPostSelectionMode)
  }

  fun onLoadingErrorUpdatedOrRemoved(screenKey: ScreenKey, hasLoadError: Boolean) {
    val hideableUiVisibilityInfo = hideableUiVisibilityInfoMap.getOrPut(
      key = screenKey,
      defaultValue = { HideableUiVisibilityInfo() }
    )

    hideableUiVisibilityInfo.update(hasLoadError = hasLoadError)
  }

  fun isDrawerOpenedOrOpening(): Boolean {
    return when (_currentDrawerVisibility) {
      is DrawerVisibility.Fling -> true
      is DrawerVisibility.Drag -> true
      DrawerVisibility.Closed,
      DrawerVisibility.Closing -> false
      DrawerVisibility.Opened,
      DrawerVisibility.Opening -> true
    }
  }

  fun isDrawerFullyOpened(): Boolean {
    return _currentDrawerVisibility is DrawerVisibility.Opened
  }

  fun openDrawer(withAnimation: Boolean = true) {
    val drawerVisibility = if (withAnimation) {
      DrawerVisibility.Opening
    } else {
      DrawerVisibility.Opened
    }

    updateDrawerVisibility(drawerVisibility)
  }

  fun closeDrawer(withAnimation: Boolean = true) {
    val drawerVisibility = if (withAnimation) {
      DrawerVisibility.Closing
    } else {
      DrawerVisibility.Closed
    }

    updateDrawerVisibility(drawerVisibility)
  }

  fun dragDrawer(isDragging: Boolean, time: Long, dragX: Float) {
    val drawerVisibility = DrawerVisibility.Drag(
      time = time,
      isDragging = isDragging,
      dragX = dragX
    )
    updateDrawerVisibility(drawerVisibility)
  }

  fun flingDrawer(velocity: Velocity) {
    val drawerVisibility = DrawerVisibility.Fling(velocity)
    updateDrawerVisibility(drawerVisibility)
  }

  private fun updateDrawerVisibility(drawerVisibility: DrawerVisibility) {
    _drawerVisibilityFlow.tryEmit(drawerVisibility)
    _currentDrawerVisibility = drawerVisibility
  }

  suspend fun updateLayoutModeAndCurrentPage(
    layoutType: LayoutType,
    orientation: Int,
    totalScreenWidth: Int,
    windowSizeClass: WindowSizeClass
  ) : MainUiLayoutMode {
    check(orientation in orientations) { "Unexpected orientation: ${orientation}" }

    val uiLayoutMode = when (layoutType) {
      LayoutType.Auto -> {
        when (windowSizeClass.widthSizeClass) {
          WindowWidthSizeClass.Compact -> MainUiLayoutMode.Phone
          WindowWidthSizeClass.Medium -> MainUiLayoutMode.Split
          WindowWidthSizeClass.Expanded -> MainUiLayoutMode.Split
          else -> MainUiLayoutMode.Phone
        }
      }
      LayoutType.Phone -> MainUiLayoutMode.Phone
      LayoutType.Split -> MainUiLayoutMode.Split
    }

    if (currentUiLayoutModeState.value == uiLayoutMode) {
      currentUiLayoutModeKnownDeferred.complete(Unit)
      return currentUiLayoutModeState.value
    }

    MainUiLayoutMode.values().forEach { layoutMode ->
      if (!_currentPageMapFlow.containsKey(layoutMode)) {
        defaultCurrentPageByUiLayoutMode(layoutMode)
      }
    }

    if (totalScreenWidth <= 0) {
      currentUiLayoutModeState.value = MainUiLayoutMode.Phone
      currentUiLayoutModeKnownDeferred.complete(Unit)

      return MainUiLayoutMode.Phone
    }

    if (
      uiLayoutMode == MainUiLayoutMode.Split &&
      windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    ) {
      _notEnoughWidthForSplitLayoutFlow.emit(windowSizeClass.widthSizeClass)
      currentUiLayoutModeState.value = MainUiLayoutMode.Phone
      appSettings.layoutType.write(LayoutType.Phone)
      currentUiLayoutModeKnownDeferred.complete(Unit)

      return MainUiLayoutMode.Phone
    }

    _notEnoughWidthForSplitLayoutFlow.emit(null)
    currentUiLayoutModeState.value = uiLayoutMode
    currentUiLayoutModeKnownDeferred.complete(Unit)

    return uiLayoutMode
  }

  private fun defaultCurrentPageByUiLayoutMode(uiLayoutMode: MainUiLayoutMode): MutableStateFlow<CurrentPage> {
    val screenKey = when (uiLayoutMode) {
      MainUiLayoutMode.Phone -> CatalogScreen.SCREEN_KEY
      MainUiLayoutMode.Split -> CatalogScreen.SCREEN_KEY
    }

    val newCurrentPage = layoutModeDependantPage(
      mainUiLayoutMode = uiLayoutMode,
      currentPage = CurrentPage(screenKey, false)
    )

    return MutableStateFlow(newCurrentPage)
  }

  private fun layoutModeDependantPage(
    mainUiLayoutMode: MainUiLayoutMode,
    currentPage: CurrentPage
  ): CurrentPage {
    return when (mainUiLayoutMode) {
      MainUiLayoutMode.Phone -> {
        currentPage
      }
      MainUiLayoutMode.Split -> {
        // When in SPLIT layout we always want to have the "current page" to be the CatalogScreen's
        // page so that stuff like pushing new screens works correctly
        if (currentPage.screenKey == ThreadScreen.SCREEN_KEY) {
          return currentPage.copy(screenKey = CatalogScreen.SCREEN_KEY)
        }

        return currentPage
      }
    }
  }

  companion object {
    const val SAVE_STATE_KEY = "com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager"

    private const val TOOLBAR_VISIBILITY_INFO_MAP = "toolbar_visibility_info_map"
    private const val CURRENT_PAGE_MAP = "current_page_map"
    private const val MAIN_UI_LAYOUT_MODE = "main_ui_layout_mode"
    private const val CURRENT_PAGE = "current_page"
    private const val IS_DRAWER_OPENED = "is_drawer_opened"

    const val CATALOG_SCREEN_WEIGHT = .5f
    const val THREAD_SCREEN_WEIGHT = .5f
  }

}

@Immutable
enum class MainUiLayoutMode(val value: Int) {
  Phone(0),
  Split(1);

  companion object {
    fun fromRawValue(rawValue: Int): MainUiLayoutMode {
      return values().firstOrNull { it.value == rawValue } ?: Phone
    }
  }
}

private data class LayoutChangingSettings(
  val orientation: Int?,
  val homeScreenLayoutType: LayoutType,
  val totalScreenWidth: Int
)