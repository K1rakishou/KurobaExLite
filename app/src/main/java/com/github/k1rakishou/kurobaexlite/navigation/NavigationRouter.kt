package com.github.k1rakishou.kurobaexlite.navigation

import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import logcat.LogPriority
import logcat.logcat

open class NavigationRouter(
  val routerKey: ScreenKey,
  private val parentRouter: NavigationRouter?
) {
  protected val _navigationScreensStack = mutableStateListOf<ComposeScreen>()
  val navigationScreensStack: List<ComposeScreen>
    get() = _navigationScreensStack

  protected val _screenAnimations = mutableStateMapOf<ScreenKey, ScreenAnimation>()
  val screenAnimations: Map<ScreenKey, ScreenAnimation>
    get() = _screenAnimations

  protected val childRouters = linkedMapOf<ScreenKey, NavigationRouter>()
  protected val removingScreens = mutableSetOf<ScreenKey>()

  @CallSuper
  open fun onAttachActivity(activity: ComponentActivity) {
    _navigationScreensStack.forEach { screen -> screen.onAttachActivity(activity) }
    childRouters.entries.forEach { (_, router) -> router.onAttachActivity(activity) }
  }

  @CallSuper
  open fun onDetachActivity() {
    _navigationScreensStack.forEach { screen -> screen.onDetachActivity() }
    childRouters.entries.forEach { (_, router) -> router.onDetachActivity() }
  }

  fun navigationScreensStackExcept(composeScreen: ComposeScreen): List<ComposeScreen> {
    return navigationScreensStack.filter { screen -> screen.screenKey != composeScreen.screenKey }
  }

  open fun screenExistsInThisRouter(screenKey: ScreenKey): Boolean {
    return navigationScreensStack.any { composeScreen -> composeScreen.screenKey == screenKey }
  }

  @CallSuper
  open fun onLifecycleCreate() {
    // When creating, first create parent screens, then child screens
    _navigationScreensStack.forEach { screen ->
      screen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Creating, true)
      screen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Created, true)
    }

    childRouters.entries.forEach { (_, childRouter) -> childRouter.onLifecycleCreate() }
  }

  @CallSuper
  open fun onLifecycleDestroy() {
    // When destroying, first destroy child screens, then parent screens
    childRouters.entries.forEach { (_, childRouter) -> childRouter.onLifecycleDestroy() }

    _navigationScreensStack.forEach { screen ->
      screen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposing, true)
      screen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposed, true)
    }
  }

  open fun pushScreen(composeScreen: ComposeScreen): Boolean {
    return pushScreen(composeScreen, true)
  }

  open fun pushScreen(composeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    if (composeScreen is FloatingComposeScreen) {
      error("FloatingComposeScreens must be added via presentScreen() function!")
    }

    val indexOfPrev = _navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == composeScreen.screenKey }

    if (indexOfPrev >= 0) {
      // Already added, update the arguments
      navigationScreensStack[indexOfPrev].onNewArguments(composeScreen.screenArgs)
      return false
    }

    val screenAnimation = if (withAnimation) {
      composeScreen.pushAnimation
    } else {
      ScreenAnimation.Set(composeScreen.screenKey)
    }

    Snapshot.withMutableSnapshot {
      _navigationScreensStack.add(composeScreen)
      _screenAnimations.put(composeScreen.screenKey, screenAnimation)
    }

    logcat(TAG, LogPriority.VERBOSE) { "pushScreen(${composeScreen.screenKey.key})" }
    composeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Creating)

    return true
  }

  open fun popScreen(composeScreen: ComposeScreen): Boolean {
    return popScreen(composeScreen, true)
  }

  open fun popScreen(composeScreen: ComposeScreen, withAnimation: Boolean): Boolean {
    val indexOfPrev = _navigationScreensStack
      .indexOfFirst { screen -> screen.screenKey == composeScreen.screenKey }

    if (indexOfPrev < 0) {
      // Already removed
      return false
    }

    if (!removingScreens.add(composeScreen.screenKey)) {
      return false
    }

    val screenAnimation = if (withAnimation) {
      composeScreen.popAnimation
    } else {
      ScreenAnimation.Remove(composeScreen.screenKey)
    }

    logcat(TAG, LogPriority.VERBOSE) { "popScreen(${composeScreen.screenKey.key})" }
    composeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposing)

    Snapshot.withMutableSnapshot {
      _screenAnimations.put(composeScreen.screenKey, screenAnimation)
    }

    return true
  }

  open fun presentScreen(floatingComposeScreen: FloatingComposeScreen) {
    if (parentRouter != null) {
      parentRouter.presentScreen(floatingComposeScreen)
      return
    }

    unreachable("Must never reach here because should be overridden by MainNavigationRouter")
  }

  open fun stopPresentingScreen(
    screenKey: ScreenKey
  ): Boolean {
    if (parentRouter != null) {
      return parentRouter.stopPresentingScreen(screenKey)
    }

    unreachable("Must never reach here because should be overridden by MainNavigationRouter")
  }

  fun childRouter(screenKey: ScreenKey): NavigationRouter {
    return childRouters.getOrPut(
      key = screenKey,
      defaultValue = {
        NavigationRouter(
          routerKey = screenKey,
          parentRouter = this
        )
      }
    )
  }

  fun getParentRouterByKey(screenKey: ScreenKey): NavigationRouter {
    if (routerKey == screenKey) {
      return this
    }

    if (parentRouter != null) {
      val router = parentRouter.getParentRouterByKeyOrNull(screenKey)
      if (router != null) {
        return router
      }
    }

    error("getParentRouterByKey() NavigationRouter with key \'${screenKey.key}\' not found")
  }

  fun getParentRouterByKeyOrNull(screenKey: ScreenKey): NavigationRouter? {
    if (routerKey == screenKey) {
      return this
    }

    if (parentRouter != null) {
      return parentRouter.getParentRouterByKeyOrNull(screenKey)
    }

    return null
  }

  fun getChildRouterByKey(screenKey: ScreenKey): NavigationRouter {
    if (routerKey == screenKey) {
      return this
    }

    for (childRouter in childRouters.values) {
      val router = childRouter.getChildRouterByKeyOrNull(screenKey)
      if (router != null) {
        return router
      }
    }

    error("getChildRouterByKey() NavigationRouter with key \'${screenKey.key}\' not found")
  }

  fun getChildRouterByKeyOrNull(screenKey: ScreenKey): NavigationRouter? {
    if (routerKey == screenKey) {
      return this
    }

    for (childRouter in childRouters.values) {
      val router = childRouter.getParentRouterByKeyOrNull(screenKey)
      if (router != null) {
        return router
      }
    }

    return null
  }

  fun getScreenByKey(screenKey: ScreenKey): ComposeScreen? {
    val rootRouter = getRootRouter()
    return getScreenByKeyInternal(rootRouter, screenKey)
  }

  fun isTopmostScreen(screen: ComposeScreen): Boolean {
    val rootRouter = getRootRouter()

    if (screen is FloatingComposeScreen) {
      return rootRouter.floatingScreensStack.lastOrNull() === screen
    }

    if (rootRouter.floatingScreensStack.isNotEmpty()) {
      return false
    }

    return navigationScreensStack.lastOrNull() === screen
  }

  private fun getScreenByKeyInternal(
    currentRouter: NavigationRouter,
    screenKey: ScreenKey
  ): ComposeScreen? {
    if (currentRouter is MainNavigationRouter) {
      val screen = currentRouter.floatingScreensStack
        .firstOrNull { floatingScreen -> floatingScreen.screenKey == screenKey }

      if (screen != null) {
        return screen
      }
    }

    val screen = currentRouter.navigationScreensStack
      .firstOrNull { floatingScreen -> floatingScreen.screenKey == screenKey }

    if (screen != null) {
      return screen
    }

    for ((_, childRouter) in currentRouter.childRouters) {
      val screen = getScreenByKeyInternal(childRouter, screenKey)
      if (screen != null) {
        return screen
      }
    }

    return null
  }

  private fun getRootRouter(): MainNavigationRouter {
    if (parentRouter == null) {
      return this as MainNavigationRouter
    }

    return parentRouter.getRootRouter()
  }

  open suspend fun onScreenAnimationFinished(screenAnimation: ScreenAnimation) {
    val prevScreenAnimation = _screenAnimations[screenAnimation.screenKey]

    if (prevScreenAnimation != null && prevScreenAnimation !== screenAnimation) {
      // During the animation the stack was changed so the ScreenAnimation was replaced with a
      // different one. In this case we don't need to do anything
      return
    }

    _screenAnimations.remove(screenAnimation.screenKey)

    val composeScreen = navigationScreensStack
      .firstOrNull { composeScreen -> composeScreen.screenKey == screenAnimation.screenKey }

    if (composeScreen == null) {
      return
    }

    if (screenAnimation.isScreenBeingRemoved()) {
      composeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Disposed)

      _navigationScreensStack
        .indexOfFirst { screen -> screen.screenKey == screenAnimation.screenKey }
        .takeIf { index -> index >= 0 }
        ?.let { indexOfRemovedScreen ->
          removingScreens.remove(screenAnimation.screenKey)
          _navigationScreensStack.removeAt(indexOfRemovedScreen)
        }

      return
    }

    composeScreen.dispatchScreenLifecycleEvent(ComposeScreen.ScreenLifecycle.Created)
  }

  protected fun <T : ComposeScreen> MutableState<List<T>>.addScreen(
    index: Int = value.lastIndex,
    newScreen: T
  ): T {
    val oldScreens = this.value
    val newScreens = oldScreens.toMutableList().apply { add(index, newScreen) }
    this.value = newScreens

    return newScreen
  }

  protected fun <T : ComposeScreen> MutableState<List<T>>.removeScreen(
    index: Int
  ): T {
    val oldScreens = this.value
    val removedScreen = oldScreens[index]

    val newScreens = oldScreens.toMutableList().apply { removeAt(index) }
    this.value = newScreens

    return removedScreen
  }

  @Stable
  sealed class ScreenAnimation {
    abstract val screenKey: ScreenKey
    abstract val animationDuration: Int

    fun isScreenBeingRemoved(): Boolean {
      return when (this) {
        is Fade -> this.fadeType == FadeType.Out
        is Pop -> true
        is Push -> false
        is Set -> false
        is Remove -> true
      }
    }

    data class Set(
      override val screenKey: ScreenKey,
      override val animationDuration: Int = 0
    ) : ScreenAnimation() {
      override fun toString(): String = "Set(key=${screenKey.key}, duration=${animationDuration})"
    }

    data class Remove(
      override val screenKey: ScreenKey,
      override val animationDuration: Int = 0
    ) : ScreenAnimation() {
      override fun toString(): String = "Remove(key=${screenKey.key}, duration=${animationDuration})"
    }

    data class Push(
      override val screenKey: ScreenKey,
      override val animationDuration: Int = defaultAnimationDuration
    ) : ScreenAnimation() {
      override fun toString(): String = "Push(key=${screenKey.key}, duration=${animationDuration})"
    }

    data class Pop(
      override val screenKey: ScreenKey,
      override val animationDuration: Int = defaultAnimationDuration
    ) : ScreenAnimation() {
      override fun toString(): String = "Pop(key=${screenKey.key}, duration=${animationDuration})"
    }

    data class Fade(
      val fadeType: FadeType,
      override val screenKey: ScreenKey,
      override val animationDuration: Int = defaultAnimationDuration
    ) : ScreenAnimation() {
      override fun toString(): String = "Fade(fadeType=${fadeType}, key=${screenKey.key}, duration=${animationDuration})"
    }

    enum class FadeType {
      In,
      Out
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as ScreenAnimation

      if (screenKey != other.screenKey) return false

      return true
    }

    override fun hashCode(): Int {
      return screenKey.hashCode()
    }

    companion object {
      private const val defaultAnimationDuration = 250
    }

  }

  companion object {
    private const val TAG = "NavigationRouter"
  }

}