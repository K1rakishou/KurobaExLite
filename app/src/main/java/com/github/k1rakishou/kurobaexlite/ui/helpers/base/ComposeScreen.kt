package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.Saver
import androidx.lifecycle.ViewModelProvider
import com.github.k1rakishou.kurobaexlite.base.GlobalConstants
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.DialogSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaSavedStateViewModelFactory
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenSavedStateViewModel
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.flow.StateFlow
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

abstract class ComposeScreen protected constructor(
  val screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  val navigationRouter: NavigationRouter
) {
  protected val globalUiInfoManager: GlobalUiInfoManager by inject(GlobalUiInfoManager::class.java)
  protected val globalConstants: GlobalConstants by inject(GlobalConstants::class.java)
  protected val appSettings: AppSettings by inject(AppSettings::class.java)
  protected val dialogSettings: DialogSettings by inject(DialogSettings::class.java)
  protected val appConstants: AppConstants by inject(AppConstants::class.java)
  protected val appResources: AppResources by inject(AppResources::class.java)
  protected val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  protected val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)

  private var _componentActivity: ComponentActivity? = componentActivity
  val componentActivity: ComponentActivity
    get() = requireNotNull(_componentActivity) { "_componentActivity was accessed while during configuration change!" }

  private val savedStateViewModel by lazy {
    val key = "${screenKey.key}_SavedStateViewModel"
    val provider = ViewModelProvider(
      componentActivity,
      KurobaSavedStateViewModelFactory(componentActivity, screenArgs)
    )

    val screenSavedStateViewModel = provider.get(key, ScreenSavedStateViewModel::class.java)
    screenSavedStateViewModel.onNewArguments(screenArgs)

    return@lazy screenSavedStateViewModel
  }

  private val backPressHandlers = mutableListOf<MainNavigationRouter.OnBackPressHandler>()
  protected val screenCoroutineScope = KurobaCoroutineScope()

  abstract val screenKey: ScreenKey

  private var _screenLifecycle = ScreenLifecycle.Disposed
  val screenLifecycle: ScreenLifecycle
    get() = _screenLifecycle

  val screenIsAlive: Boolean
    get() {
      return when (screenLifecycle) {
        ScreenLifecycle.Creating,
        ScreenLifecycle.Created -> true
        ScreenLifecycle.Disposing,
        ScreenLifecycle.Disposed -> false
      }
    }

  open val pushAnimation: NavigationRouter.ScreenAnimation
    get() = NavigationRouter.ScreenAnimation.Push(screenKey)

  open val popAnimation: NavigationRouter.ScreenAnimation
    get() = NavigationRouter.ScreenAnimation.Pop(screenKey)

  fun onAttachActivity(activity: ComponentActivity) {
    _componentActivity = activity
  }

  fun onDetachActivity() {
    _componentActivity = null
  }

  fun onNewArguments(newArgs: Bundle?) {
    savedStateViewModel.onNewArguments(newArgs)
  }

  fun <T : Any> setArgument(key: String, value: T) {
    savedStateViewModel.setArgument(key, value)
  }

  fun <T : Any> argumentOrNull(key: String): T? {
    return savedStateViewModel.getArgumentOrNull(key)
  }

  fun <T : Any> argumentOrDefault(key: String, default: T): T {
    return savedStateViewModel.getArgumentOrNull(key) ?: default
  }

  fun <T : Any> requireArgument(key: String): Lazy<T> {
    return lazy {
      val arg = argumentOrNull<T>(key)

      return@lazy checkNotNull(arg) {
        "Required argument on screen: ${screenKey} with key: ${key} is null"
      }
    }
  }

  protected fun <T : Any> argumentOrNullLazy(key: String): Lazy<T?> {
    return lazy { savedStateViewModel.getArgumentOrNull(key) }
  }

  protected fun <T : Any> argumentOrDefaultLazy(key: String, default: T): Lazy<T> {
    return lazy { savedStateViewModel.getArgumentOrNull(key) ?: default }
  }

  protected fun <T : Any> requireArgumentLazy(key: String): Lazy<T> {
    return lazy {
      val arg = argumentOrNullLazy<T>(key).value

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

  protected fun <T : Any?> saveable(
    key: String,
    saver: Saver<T, out Any> = ScreenSavedStateViewModel.saver<T>(),
    init: () -> T,
  ): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> {
    return savedStateViewModel.saveable(key, saver, init)
  }

  @Composable
  fun HandleBackPresses(backPressHandler: MainNavigationRouter.OnBackPressHandler) {
    DisposableEffect(
      key1 = backPressHandler,
      effect = {
        val localBackPressHandler = backPressHandler

        backPressHandlers += localBackPressHandler
        onDispose { backPressHandlers -= localBackPressHandler }
      }
    )
  }

  fun dispatchScreenLifecycleEvent(
    screenLifecycle: ScreenLifecycle,
    isLifecycleEvent: Boolean = false
  ) {
    if (_screenLifecycle == screenLifecycle) {
      return
    }

    _screenLifecycle = screenLifecycle

    when (screenLifecycle) {
      ScreenLifecycle.Creating,
      ScreenLifecycle.Created -> {
        val screenCreateEvent = if (isLifecycleEvent) {
          ScreenCreateEvent.LifecycleEvent
        } else {
          ScreenCreateEvent.AddToNavStack
        }

        when (screenLifecycle) {
          ScreenLifecycle.Creating -> onStartCreating(screenCreateEvent)
          ScreenLifecycle.Created -> onCreated(screenCreateEvent)
          else -> error("Unexpected screenLifecycle: ${screenLifecycle}")
        }
      }
      ScreenLifecycle.Disposing,
      ScreenLifecycle.Disposed -> {
        val screenDisposeEvent = if (isLifecycleEvent) {
          ScreenDisposeEvent.LifecycleEvent
        } else {
          ScreenDisposeEvent.RemoveFromNavStack
        }

        when (screenLifecycle) {
          ScreenLifecycle.Disposing -> onStartDisposing(screenDisposeEvent)
          ScreenLifecycle.Disposed -> onDisposed(screenDisposeEvent)
          else -> error("Unexpected screenLifecycle: ${screenLifecycle}")
        }
      }
    }
  }

  /**
   * [onStartCreating] is called when the screen is added to the stack before the animation started
   * playing
   * */
  @CallSuper
  protected open fun onStartCreating(screenCreateEvent: ScreenCreateEvent) {
    _screenLifecycle = ScreenLifecycle.Creating
    logcat(TAG, LogPriority.VERBOSE) { "onStartCreating(${screenKey.key}, ${screenCreateEvent})" }
  }

  /**
   * [onCreated] is called after the creation animation finished playing
   * */
  @CallSuper
  protected open fun onCreated(screenCreateEvent: ScreenCreateEvent) {
    _screenLifecycle = ScreenLifecycle.Created
    logcat(TAG, LogPriority.VERBOSE) { "onCreated(${screenKey.key}, ${screenCreateEvent})" }
  }

  /**
   * [onStartDisposing] is called when the screen is removed from the stack before the animation
   * started playing
   * */
  @CallSuper
  protected open fun onStartDisposing(screenDisposeEvent: ScreenDisposeEvent) {
    _screenLifecycle = ScreenLifecycle.Disposing
    logcat(TAG, LogPriority.VERBOSE) { "onStartDisposing(${screenKey.key}, ${screenDisposeEvent})" }
  }

  /**
   * [onCreated] is called after the disposing animation finished playing.
   * When overriding, the "super" method must be called at the end of the overridden onDisposed() method.
   * */
  @CallSuper
  protected open fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    _screenLifecycle = ScreenLifecycle.Disposed
    logcat(TAG, LogPriority.VERBOSE) { "onDisposed(${screenKey.key}, ${screenDisposeEvent})" }

    backPressHandlers.clear()

    // When screen is being removed from the nav stack (normally) we need to clear it's SavedStateHandle
    // to clear all cached data so that we don't get the same data on the next screen creation
    if (screenDisposeEvent == ScreenDisposeEvent.RemoveFromNavStack) {
      savedStateViewModel.removeSavedStateHandle()
      screenCoroutineScope.cancelChildren()
    }

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

  protected fun <T> Result<T>.toastOnError(
    longToast: Boolean = false,
    printStacktrace: Boolean = true,
    message: ((Throwable) -> String)? = null
  ): Result<T> {
    if (isFailure) {
      val exception = this.exceptionOrThrow()

      val actualMessage = message?.invoke(exception)
        ?: this.exceptionOrThrow().errorMessageOrClassName(userReadable = true)

      val duration = if (longToast) {
        SnackbarManager.STANDARD_DELAY
      } else {
        SnackbarManager.SHORT_DELAY
      }

      if (printStacktrace) {
        logcatError(TAG) { "[${screenKey.key}] toastOnError() error: ${exception.asLogIfImportantOrErrorMessage()}" }
      } else {
        logcatError(TAG) { "[${screenKey.key}] toastOnError() message: ${actualMessage}" }
      }

      snackbarManager.errorToast(
        message = actualMessage,
        screenKey = screenKey,
        duration = duration.milliseconds
      )
    }

    return this
  }

  protected fun <T> Result<T>.toastOnSuccess(
    longToast: Boolean = false,
    message: () -> String
  ): Result<T> {
    if (isSuccess) {
      val duration = if (longToast) {
        SnackbarManager.STANDARD_DELAY
      } else {
        SnackbarManager.SHORT_DELAY
      }

      snackbarManager.toast(
        message = message(),
        screenKey = screenKey,
        duration = duration.milliseconds
      )
    }

    return this
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

  enum class ScreenCreateEvent {
    AddToNavStack,
    LifecycleEvent
  }

  enum class ScreenDisposeEvent {
    LifecycleEvent,
    RemoveFromNavStack
  }

  enum class ScreenLifecycle {
    Creating,
    Created,
    Disposing,
    Disposed
  }

  companion object {
    private const val TAG = "ComposeScreen"
    
    inline fun <reified T : ComposeScreen> createScreen(
      componentActivity: ComponentActivity,
      navigationRouter: NavigationRouter,
      noinline args: (Bundle.() -> Unit)? = null,
      noinline callbacks: (CallbackBuilder.() -> Unit)? = null
    ): T {
      return createScreenInternal(
        screenClass = T::class,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        args = args,
        callbacks = callbacks
      )
    }

    @PublishedApi
    internal fun <T : ComposeScreen> createScreenInternal(
      screenClass: KClass<T>,
      componentActivity: ComponentActivity,
      navigationRouter: NavigationRouter,
      args: (Bundle.() -> Unit)? = null,
      callbacks: (CallbackBuilder.() -> Unit)? = null
    ): T {
      val constructor = screenClass.constructors.firstOrNull()
      if (constructor == null) {
        error("Screen of class ${screenClass.simpleName} has no constructors")
      }

      val screenArgs = if (args != null) {
        Bundle().apply { args(this) }
      } else {
        null
      }

      val createdScreen = try {
        constructor.call(screenArgs, componentActivity, navigationRouter)
      } catch (error: Throwable) {
        val actualError = extractActualError(error)

        throw RuntimeException("Failed to create screen of class: ${screenClass.simpleName}, error: ${actualError}")
      }

      if (callbacks != null) {
        val callbackBuilder = CallbackBuilder(createdScreen.screenKey)
        callbacks(callbackBuilder)
      }

      return createdScreen
    }

    private fun extractActualError(error: Throwable): Throwable {
      var cause: Throwable? = error

      while (true) {
        cause = cause?.cause ?: break
      }

      return cause ?: error
    }

  }

}