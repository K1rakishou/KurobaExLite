package com.github.k1rakishou.kurobaexlite.features.posts.bookmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeCheckbox
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.launch

class NewBookmarkOptionsScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY
  override val contentAlignment: Alignment = touchPositionDependantAlignment

  @Composable
  override fun FloatingContent() {
    super.FloatingContent()

    val coroutineScope = rememberCoroutineScope()
    val width = if (globalUiInfoManager.isTablet) 360.dp else 280.dp

    var automaticallyStartWatchingBookmarksMut by remember { mutableStateOf<Boolean?>(null) }
    val automaticallyStartWatchingBookmarks = automaticallyStartWatchingBookmarksMut
    var doNotShowNewBookmarkDialogOptionsMut by remember { mutableStateOf<Boolean?>(null) }
    val doNotShowNewBookmarkDialogOptions = doNotShowNewBookmarkDialogOptionsMut

    val disabledAlpha = ContentAlpha.disabled
    val enabledAlpha = ContentAlpha.high

    LaunchedEffect(
      key1 = Unit,
      block = {
        automaticallyStartWatchingBookmarksMut = appSettings.automaticallyStartWatchingBookmarks.read()
        doNotShowNewBookmarkDialogOptionsMut = dialogSettings.doNotShowNewBookmarkDialogOptions.read()
      }
    )

    Column(
      modifier = Modifier
        .padding(8.dp)
        .width(width)
        .wrapContentHeight()
    ) {
      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 42.dp)
          .padding(horizontal = 8.dp, vertical = 8.dp)
          .graphicsLayer {
            alpha = if (automaticallyStartWatchingBookmarks == null) {
              disabledAlpha
            } else {
              enabledAlpha
            }
          },
        enabled = automaticallyStartWatchingBookmarks != null,
        currentlyChecked = automaticallyStartWatchingBookmarks == true,
        text = stringResource(id = R.string.new_bookmark_options_screen_start_watching_thread),
        onCheckChanged = {
          if (automaticallyStartWatchingBookmarks == null) {
            return@KurobaComposeCheckbox
          }

          automaticallyStartWatchingBookmarksMut = !automaticallyStartWatchingBookmarks
        }
      )

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        KurobaComposeCheckbox(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 42.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .graphicsLayer {
              alpha = if (doNotShowNewBookmarkDialogOptions == null) {
                disabledAlpha
              } else {
                enabledAlpha
              }
            },
          enabled = doNotShowNewBookmarkDialogOptions != null,
          currentlyChecked = doNotShowNewBookmarkDialogOptions == true,
          text = stringResource(id = R.string.new_bookmark_options_screen_do_not_show_this_dialog),
          onCheckChanged = {
            if (doNotShowNewBookmarkDialogOptions == null) {
              return@KurobaComposeCheckbox
            }

            doNotShowNewBookmarkDialogOptionsMut = !doNotShowNewBookmarkDialogOptions
          }
        )

        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeClickableIcon(
          drawableId = R.drawable.ic_baseline_help_outline_24,
          onClick = {
            snackbarManager.toast(messageId = R.string.new_bookmark_options_screen_do_not_show_this_dialog_hint)
          }
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      Row(horizontalArrangement = Arrangement.End) {
        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          text = stringResource(id = R.string.cancel)
        ) {
          stopPresenting()
        }

        Spacer(modifier = Modifier.width(8.dp))

        val okButtonEnabled = automaticallyStartWatchingBookmarks != null
          && doNotShowNewBookmarkDialogOptions != null

        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          enabled = okButtonEnabled,
          text = stringResource(id = R.string.ok)
        ) {
          coroutineScope.launch {
            appSettings.automaticallyStartWatchingBookmarks.write(automaticallyStartWatchingBookmarks!!)
            dialogSettings.doNotShowNewBookmarkDialogOptions.write(doNotShowNewBookmarkDialogOptions!!)

            ScreenCallbackStorage.invokeCallback(screenKey, ON_FINISHED)
            stopPresenting()
          }
        }
      }
    }
  }

  companion object {
    private const val TAG = "NewBookmarkOptionsScreen"

    const val ON_FINISHED = "on_finished"

    val SCREEN_KEY = ScreenKey("NewBookmarkOptionsScreen")
  }
}