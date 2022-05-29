package com.github.k1rakishou.kurobaexlite.features.settings

import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreenBuilder
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreens
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class AppSettingsScreenViewModel : BaseViewModel() {
  private val appSettings: AppSettings by inject(AppSettings::class.java)
  private val appResources: AppResources by inject(AppResources::class.java)

  private val _builtSettings = ConcurrentHashMap<SettingScreens, MutableStateFlow<SettingScreen?>>()

  private val _currentScreen = MutableStateFlow<SettingScreens>(SettingScreens.Main)
  val currentScreen: StateFlow<SettingScreens>
    get() = _currentScreen.asStateFlow()

  private val _showMenuItemsFlow = MutableSharedFlow<DisplayedMenuOptions>(extraBufferCapacity = Channel.RENDEZVOUS)
  val showMenuItemsFlow: SharedFlow<DisplayedMenuOptions>
    get() = _showMenuItemsFlow.asSharedFlow()

  init {
    SettingScreens.values().forEach { settingScreens ->
      _builtSettings[settingScreens] = MutableStateFlow(null)
    }

    viewModelScope.launch(Dispatchers.IO) {
      buildMainSettingsScreen()
    }
  }

  fun settingScreen(key: SettingScreens): StateFlow<SettingScreen?> {
    return _builtSettings[key]!!.asStateFlow()
  }

  private fun buildMainSettingsScreen() {
    val key = SettingScreens.Main

    if (_builtSettings[key]!!.value != null) {
      return
    }

    val builder = SettingScreenBuilder(SettingScreens.Main)
    builder.group(
      groupKey = "main_group",
      groupName = appResources.string(R.string.settings_screen_history_group),
      builder = {
        boolean(
          title = appResources.string(R.string.settings_screen_history_enabled),
          delegate = appSettings.historyEnabled
        )

        boolean(
          title = appResources.string(R.string.settings_screen_history_on_left_side),
          subtitle = appResources.string(R.string.settings_screen_history_on_left_side_description),
          delegate = appSettings.historyScreenOnLeftSide,
          dependencies = listOf(appSettings.historyEnabled)
        )
      }
    )
    builder.group(
      groupKey = "layout_group",
      groupName = appResources.string(R.string.settings_screen_ui_layout_group),
      builder = {
        list(
          title = appResources.string(R.string.settings_screen_layout_mode),
          delegate = appSettings.layoutType,
          showOptionsScreen = { items -> displayOptionsAndWaitForSelection(items) }
        )
      }
    )

    _builtSettings[key]!!.value = builder.build()
  }

  private suspend fun displayOptionsAndWaitForSelection(items: List<FloatingMenuItem>): String? {
    val completableDeferred = CompletableDeferred<String?>()

    val displayedMenuOptions = DisplayedMenuOptions(
      items = items,
      result = completableDeferred
    )

    _showMenuItemsFlow.emit(displayedMenuOptions)

    return try {
      completableDeferred.await()
    } catch (error: Throwable) {
      null
    }
  }

  class DisplayedMenuOptions(
    val items: List<FloatingMenuItem>,
    val result: CompletableDeferred<String?>
  )

}