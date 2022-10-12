package com.github.k1rakishou.kurobaexlite.features.bookmarks

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.DrawerContentType
import com.github.k1rakishou.kurobaexlite.helpers.util.TaskType
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.rememberCoroutineTask
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeClickableIcon


@Composable
fun DrawerContentTypeToggleIcon(
  iconSize: Dp
) {
  val appSettings = koinRemember<AppSettings>()

  val drawerContentTypeMut by appSettings.drawerContentType.listen().collectAsState(initial = null)
  val drawerContentType = drawerContentTypeMut

  if (drawerContentType == null) {
    return
  }

  val coroutineTask = rememberCoroutineTask(taskType = TaskType.SingleInstance)

  val drawableId = when (drawerContentType) {
    DrawerContentType.History -> R.drawable.ic_baseline_bookmark_border_24
    DrawerContentType.Bookmarks -> R.drawable.ic_baseline_history_24
  }

  KurobaComposeClickableIcon(
    modifier = Modifier.size(iconSize),
    drawableId = drawableId,
    enabled = !coroutineTask.isRunning,
    onClick = {
      coroutineTask.launch {
        val newDrawerContentType = when (drawerContentType) {
          DrawerContentType.History -> DrawerContentType.Bookmarks
          DrawerContentType.Bookmarks -> DrawerContentType.History
        }

        appSettings.drawerContentType.write(newDrawerContentType)
      }
    }
  )
}