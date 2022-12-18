package com.github.k1rakishou.kurobaexlite.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.helpers.AppRestarter
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.RuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.executors.RendezvousCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.executors.SerializedCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.picker.LocalFilePicker
import com.github.k1rakishou.kurobaexlite.managers.GlobalUiInfoManager
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UpdateManager
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.ProvideEverything
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
  private val mainActivityViewModel: MainActivityViewModel by viewModel()

  private val globalUiInfoManager: GlobalUiInfoManager by inject(GlobalUiInfoManager::class.java)
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  private val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)
  private val fullScreenHelpers: FullScreenHelpers by inject(FullScreenHelpers::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)
  private val localFilePicker: LocalFilePicker by inject(LocalFilePicker::class.java)
  private val mainActivityIntentHandler: MainActivityIntentHandler by inject(MainActivityIntentHandler::class.java)
  private val updateManager: UpdateManager by inject(UpdateManager::class.java)
  private val appRestarter: AppRestarter by inject(AppRestarter::class.java)

  private var backPressedOnce = false

  private val handler = Handler(Looper.getMainLooper())
  private val coroutineScope = KurobaCoroutineScope()
  private val backPressExecutor = RendezvousCoroutineExecutor(coroutineScope)
  private val intentExecutor = SerializedCoroutineExecutor(coroutineScope)

  private val runtimePermissionsHelper by lazy { RuntimePermissionsHelper(applicationContext, this) }

  init {
    processSaveableComponents()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val isFreshStart = savedInstanceState == null
    logcat(TAG) { "onCreate() start isFreshStart: $isFreshStart" }

    localFilePicker.attachActivity(this)
    mainActivityIntentHandler.onCreate(this)
    updateManager.checkForUpdates()

    fullScreenHelpers.setupEdgeToEdge(window = window)
    fullScreenHelpers.setupStatusAndNavBarColors(theme = themeEngine.chanTheme, window = window)
    appRestarter.attachActivity(this)

    clickedThumbnailBoundsStorage.clear()

    mainActivityViewModel.onAttachActivity(this)
    mainActivityViewModel.onLifecycleCreate()

    setContent {
      ProvideEverything(
        componentActivity = this,
        window = window,
        themeEngine = themeEngine,
        runtimePermissionsHelper = runtimePermissionsHelper
      ) {
        val mainScreen = remember {
          ComposeScreen.createScreen<MainScreen>(
            componentActivity = this,
            navigationRouter = mainActivityViewModel.rootNavigationRouter
          )
        }

        mainScreen.Content()
      }
    }

    if (intent != null) {
      onNewIntent(intent)
    }
  }

  override fun onDestroy() {
    logcat(TAG) { "onDestroy()" }

    coroutineScope.cancelChildren()
    clickedThumbnailBoundsStorage.clear()
    localFilePicker.detachActivity()
    mainActivityIntentHandler.onDestroy()
    appRestarter.detachActivity()

    mainActivityViewModel.onLifecycleDestroy()
    mainActivityViewModel.onDetachActivity()

    super.onDestroy()
  }

  override fun onNewIntent(intent: Intent?) {
    if (intent == null) {
      super.onNewIntent(intent)
      return
    }

    intentExecutor.post {
      if (mainActivityIntentHandler.onNewIntent(intent)) {
        return@post
      }

      super.onNewIntent(intent)
    }
  }

  override fun onBackPressed() {
    backPressExecutor.post {
      if (mainActivityViewModel.onBackBackPressed()) {
        return@post
      }

      if (!backPressedOnce) {
        backPressedOnce = true

        snackbarManager.toast(
          screenKey = MainScreen.SCREEN_KEY,
          messageId = R.string.main_activity_press_back_again_to_exit,
          toastId = pressBackMessageToastId,
          duration = pressBackFlagResetDuration
        )

        handler.postDelayed(
          { backPressedOnce = false },
          pressBackFlagResetDuration.inWholeMilliseconds
        )

        return@post
      }

      finish()
    }
  }

  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    if (ev != null) {
      globalUiInfoManager.setLastTouchPosition(ev.x, ev.y)
    }

    return super.dispatchTouchEvent(ev)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    runtimePermissionsHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    localFilePicker.onActivityResult(requestCode, resultCode, data)
  }

  private fun processSaveableComponents() {
    savedStateRegistry.registerSavedStateProvider(globalUiInfoManager.saveableComponentKey) {
      globalUiInfoManager.saveState()
    }
    addOnContextAvailableListener {
      globalUiInfoManager.restoreFromState(
        savedStateRegistry.consumeRestoredStateForKey(globalUiInfoManager.saveableComponentKey)
      )
    }
  }

  companion object {
    private const val TAG = "MainActivity"
    private val pressBackFlagResetDuration = 1.seconds

    private const val pressBackMessageToastId = "press_back_to_exit_message_toast"
  }

}