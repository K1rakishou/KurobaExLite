package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.lifecycle.ViewModelProvider
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaSavedStateViewModelFactory
import com.github.k1rakishou.kurobaexlite.ui.helpers.SavedStateViewModel
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.StateFlow
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

abstract class ComposeScreen protected constructor(
  screenArgs: Bundle? = null,
  val componentActivity: ComponentActivity,
  val navigationRouter: NavigationRouter
) {
  protected val globalUiInfoManager: GlobalUiInfoManager by inject(GlobalUiInfoManager::class.java)
  protected val globalConstants: GlobalConstants by inject(GlobalConstants::class.java)
  protected val appSettings: AppSettings by inject(AppSettings::class.java)
  protected val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)

  private val savedStateViewModel by lazy {
    val key = "${screenKey.key}_SavedStateViewModel"
    val provider = ViewModelProvider(componentActivity, KurobaSavedStateViewModelFactory(componentActivity, screenArgs))

    return@lazy provider.get(key, SavedStateViewModel::class.java)
  }

  private val backPressHandlers = mutableListOf<MainNavigationRouter.OnBackPressHandler>()
  protected val screenCoroutineScope = KurobaCoroutineScope()

  abstract val screenKey: ScreenKey

  protected fun <T : Any> argumentOrNull(key: String): Lazy<T?> {
    return lazy { savedStateViewModel.getArgumentOrNull(key) }
  }

  protected fun <T : Any> requireArgument(key: String): Lazy<T> {
    return lazy {
      val arg = argumentOrNull<T>(key).value

      return@lazy checkNotNull(arg) {
        "Required argument on screen: ${screenKey} with key: ${key} is null"
      }
    }
  }

  protected fun <T : Any?> listenForArgumentsNullable(
    key: String,
    initialValue: T
  ): StateFlow<T> {
    return savedStateViewModel.listenForArgumentsAsStateFlow(key, initialValue)
  }

  protected fun <T : Any> saveable(
    key: String,
    saver: Saver<T, out Any> = autoSaver<T>(),
    init: () -> T,
  ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> {
    return savedStateViewModel.saveable(key, saver, init)
  }

  protected fun <T : Any> mutableSaveable(
    key: String,
    saver: Saver<T, out Any> = autoSaver<T>(),
    init: () -> T,
  ): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> {
    return savedStateViewModel.mutableSaveable(key, saver, init)
  }

  @Composable
  fun HandleBackPresses(backPressHandler: MainNavigationRouter.OnBackPressHandler) {
    DisposableEffect(
      key1 = Unit,
      effect = {
        backPressHandlers += backPressHandler
        onDispose { backPressHandlers -= backPressHandler }
      }
    )
  }

  /**
   * [onStartCreating] is called when the screen is added to the stack before the animation started
   * playing
   * */
  @CallSuper
  open fun onStartCreating() {
    logcat(TAG, LogPriority.VERBOSE) { "onStartCreating(${screenKey.key})" }
  }

  /**
   * [onCreated] is called after the creation animation finished playing
   * */
  @CallSuper
  open fun onCreated() {
    logcat(TAG, LogPriority.VERBOSE) { "onCreated(${screenKey.key})" }
  }

  /**
   * [onStartDisposing] is called when the screen is removed from the stack before the animation
   * started playing
   * */
  @CallSuper
  open fun onStartDisposing() {
    logcat(TAG, LogPriority.VERBOSE) { "onStartDisposing(${screenKey.key})" }
  }

  /**
   * [onCreated] is called after the disposing animation finished playing
   * */
  @CallSuper
  open fun onDisposed() {
    logcat(TAG, LogPriority.VERBOSE) { "onDisposed(${screenKey.key})" }

    screenCoroutineScope.cancelChildren()
    ScreenCallbackStorage.onScreenDisposed(screenKey)
  }

  @Composable
  abstract fun Content()

  protected fun popScreen(): Boolean {
    if (this is FloatingComposeScreen) {
      error("Can't pop FloatingComposeScreen, use stopPresenting()")
    }

    return navigationRouter.popScreen(this)
  }

  suspend fun onBackPressed(): Boolean {
    for (backPressHandler in backPressHandlers) {
      if (backPressHandler.onBackPressed()) {
        return true
      }
    }

    return false
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ComposeScreen

    if (screenKey != other.screenKey) return false

    return true
  }

  override fun hashCode(): Int {
    return screenKey.hashCode()
  }

  class CallbackBuilder(
    private val screenKey: ScreenKey
  ) {

    fun callback(callbackKey: String, func: () -> Unit) {
      val rememberableCallback = ScreenCallbackStorage.RememberableCallback0 { func() }

      ScreenCallbackStorage.rememberCallback(
        screenKey = screenKey,
        callbackKey = callbackKey,
        callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
      )
    }

    fun <T1 : Any> callback(callbackKey: String, func: (T1) -> Unit) {
      val rememberableCallback = ScreenCallbackStorage.RememberableCallback1<T1> { p1 -> func(p1) }

      ScreenCallbackStorage.rememberCallback(
        screenKey = screenKey,
        callbackKey = callbackKey,
        callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
      )
    }

    fun <T1 : Any, T2: Any> callback(callbackKey: String, func: (T1, T2) -> Unit) {
      val rememberableCallback = ScreenCallbackStorage.RememberableCallback2<T1, T2> { p1, p2 -> func(p1, p2) }

      ScreenCallbackStorage.rememberCallback(
        screenKey = screenKey,
        callbackKey = callbackKey,
        callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
      )
    }

  }

  companion object {
    private const val TAG = "ComposeScreen"
    
    inline fun <reified T : ComposeScreen> createScreen(
      componentActivity: ComponentActivity,
      navigationRouter: NavigationRouter,
      noinline args: (Bundle.() -> Unit)? = null,
      noinline callbacks: ( CallbackBuilder.() -> Unit)? = null
    ): T {
      return createScreenInternal(
        screenClass = T::class,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        args = args,
        callbacks = callbacks
      )
    }

    fun <T : ComposeScreen> createScreenInternal(
      screenClass: KClass<T>,
      componentActivity: ComponentActivity,
      navigationRouter: NavigationRouter,
      args: (Bundle.() -> Unit)? = null,
      callbacks: ( CallbackBuilder.() -> Unit)? = null
    ): T {
      val screenArgs = if (args != null) {
        val bundle = Bundle()
        args(bundle)
        bundle
      } else {
        null
      }

      val constructor = screenClass.constructors.first()
      val createdScreen = constructor.call(screenArgs, componentActivity, navigationRouter)

      if (callbacks != null) {
        val callbackBuilder = CallbackBuilder(createdScreen.screenKey)
        callbacks(callbackBuilder)
      }

      return createdScreen
    }

  }

}