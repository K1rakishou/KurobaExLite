package com.github.k1rakishou.kurobaexlite.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.media.helpers.ClickedThumbnailBoundsStorage
import com.github.k1rakishou.kurobaexlite.helpers.FullScreenHelpers
import com.github.k1rakishou.kurobaexlite.helpers.RuntimePermissionsHelper
import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.executors.RendezvousCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UiInfoManager
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.helpers.ProvideAllTheStuff
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
  private val mainActivityViewModel by viewModels<MainActivityViewModel>()
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  private val themeEngine: ThemeEngine by inject(ThemeEngine::class.java)
  private val uiInfoManager: UiInfoManager by inject(UiInfoManager::class.java)
  private val fullScreenHelpers: FullScreenHelpers by inject(FullScreenHelpers::class.java)
  private val clickedThumbnailBoundsStorage: ClickedThumbnailBoundsStorage by inject(ClickedThumbnailBoundsStorage::class.java)

  private var backPressedOnce = false

  private val handler = Handler(Looper.getMainLooper())
  private val coroutineScope = KurobaCoroutineScope()
  private val backPressExecutor = RendezvousCoroutineExecutor(coroutineScope)

  private val runtimePermissionsHelper by lazy { RuntimePermissionsHelper(applicationContext, this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    WindowCompat.setDecorFitsSystemWindows(window, false)
    fullScreenHelpers.setupEdgeToEdge(window = window)
    fullScreenHelpers.setupStatusAndNavBarColors(theme = themeEngine.chanTheme, window = window)

    clickedThumbnailBoundsStorage.clear()

    setContent {
      HandleOrientationChanges()

      ProvideAllTheStuff(
        componentActivity = this,
        window = window,
        themeEngine = themeEngine,
        runtimePermissionsHelper = runtimePermissionsHelper
      ) {
        val mainScreen = remember { MainScreen(this, mainActivityViewModel.rootNavigationRouter) }
        mainScreen.Content()
      }
    }

    if (intent != null) {
      onNewIntent(intent)
    }
  }

  @Composable
  private fun HandleOrientationChanges() {
    val orientation = LocalConfiguration.current.orientation

    DisposableEffect(
      key1 = orientation,
      effect = {
        if (orientation in uiInfoManager.orientations) {
          uiInfoManager.currentOrientation.value = orientation
        }

        // We need to reset the currentOrientation upon configuration change (screen rotation) so
        // that the layout is set into the default null state and nothing is drawn. We do that
        // to avoid situations when a layout mode is built for incorrect orientation for split
        // second after the orientation changes. This leads to nasty bugs like the search toolbar
        // query not being restored upon configuration change and other stuff.
        onDispose {
          uiInfoManager.currentOrientation.value = null
        }
      }
    )
  }

  override fun onDestroy() {
    mainActivityViewModel.rootNavigationRouter.onDestroy()
    coroutineScope.cancelChildren()
    clickedThumbnailBoundsStorage.clear()

    super.onDestroy()
  }

  override fun onNewIntent(intent: Intent?) {
    if (intent != null) {
      mainActivityViewModel.rootNavigationRouter.onNewIntent(intent)
    }
  }

  override fun onBackPressed() {
    backPressExecutor.post {
      if (mainActivityViewModel.rootNavigationRouter.onBackBackPressed()) {
        return@post
      }

      if (!backPressedOnce) {
        backPressedOnce = true

        snackbarManager.toast(
          messageId = R.string.main_activity_press_back_again_to_exit,
          toastId = pressBackMessageToastId,
          duration = pressBackFlagResetTimeMs
        )

        handler.postDelayed({ backPressedOnce = false }, pressBackFlagResetTimeMs.toLong())
        return@post
      }

      finish()
    }
  }

  override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    if (ev != null) {
      uiInfoManager.setLastTouchPosition(ev.x, ev.y)
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

  companion object {
    private const val pressBackFlagResetTimeMs = 1000

    private const val pressBackMessageToastId = "press_back_to_exit_message_toast"
  }

}