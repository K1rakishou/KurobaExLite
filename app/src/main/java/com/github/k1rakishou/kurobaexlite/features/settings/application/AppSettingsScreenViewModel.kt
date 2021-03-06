package com.github.k1rakishou.kurobaexlite.features.settings.application

import android.content.Context
import android.os.Build
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreenBuilder
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreens
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.WatcherBg
import com.github.k1rakishou.kurobaexlite.helpers.settings.WatcherFg
import com.github.k1rakishou.kurobaexlite.helpers.worker.BookmarkBackgroundWatcherWorker
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UpdateManager
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
  private val context: Context by inject(Context::class.java)
  private val appSettings: AppSettings by inject(AppSettings::class.java)
  private val androidHelpers: AndroidHelpers by inject(AndroidHelpers::class.java)
  private val appResources: AppResources by inject(AppResources::class.java)
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  private val updateManager: UpdateManager by inject(UpdateManager::class.java)

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
      groupKey = "thread_watcher",
      groupName = appResources.string(R.string.settings_screen_thread_watcher_group),
      groupDescription = appResources.string(R.string.settings_screen_thread_watcher_group_description),
      builder = {
        enum(
          title = appResources.string(R.string.settings_screen_foreground_watcher_update_interval),
          delegate = appSettings.watcherIntervalForegroundSeconds,
          showOptionsScreen = { items -> displayOptionsAndWaitForSelection(items) },
          settingNameMapper = { enum ->
            when (enum as WatcherFg) {
              WatcherFg.SEC_30 -> "30 seconds"
              WatcherFg.SEC_60 -> "60 seconds"
              WatcherFg.SEC_90 -> "90 seconds"
              WatcherFg.SEC_120 -> "2 minutes"
              WatcherFg.SEC_240 -> "4 minutes"
              WatcherFg.SEC_300 -> "5 minutes"
            }
          },
          onSettingUpdated = {
            BookmarkBackgroundWatcherWorker.restartBackgroundWork(
              appContext = context.applicationContext,
              flavorType = androidHelpers.getFlavorType(),
              appSettings = appSettings,
              isInForeground = true,
              addInitialDelay = true
            )
          }
        )

        enum(
          title = appResources.string(R.string.settings_screen_background_watcher_update_interval),
          delegate = appSettings.watcherIntervalBackgroundSeconds,
          showOptionsScreen = { items -> displayOptionsAndWaitForSelection(items) },
          settingNameMapper = { enum ->
            when (enum as WatcherBg) {
              WatcherBg.MIN_15 -> "15 minutes"
              WatcherBg.MIN_30 -> "30 minutes"
              WatcherBg.MIN_45 -> "45 minutes"
              WatcherBg.MIN_60 -> "1 hour"
              WatcherBg.MIN_120 -> "2 hours"
            }
          }
        )

        boolean(
          title = appResources.string(R.string.settings_screen_reply_notifications),
          delegate = appSettings.replyNotifications
        )
      }
    )
    builder.group(
      groupKey = "history_group",
      groupName = appResources.string(R.string.settings_screen_history_group),
      builder = {
        boolean(
          title = appResources.string(R.string.settings_screen_history_enabled),
          delegate = appSettings.historyEnabled
        )

        boolean(
          title = appResources.string(R.string.settings_screen_history_on_left_side),
          subtitleBuilder = {
            append(appResources.string(R.string.settings_screen_history_on_left_side_description))
          },
          delegate = appSettings.historyScreenOnLeftSide,
          dependencies = listOf(appSettings.historyEnabled)
        )
      }
    )
    builder.group(
      groupKey = "interface_group",
      groupName = appResources.string(R.string.settings_screen_ui_group),
      builder = {
        enum(
          title = appResources.string(R.string.settings_screen_layout_mode),
          delegate = appSettings.layoutType,
          showOptionsScreen = { items -> displayOptionsAndWaitForSelection(items) }
        )
      }
    )
    builder.group(
      groupKey = "about_group",
      groupName = appResources.string(R.string.settings_screen_about_layout_group),
      builder = {
        link(
          key = "check_for_updates",
          title = appResources.string(
            R.string.settings_screen_check_for_updates,
            BuildConfig.VERSION_NAME,
            Build.CPU_ABI,
            BuildConfig.FLAVOR
          ),
          subtitleBuilder = {
            append(appResources.string(R.string.settings_screen_check_for_updates_description))
          },
          onClicked = {
            updateManager.checkForUpdates(
              forced = true,
              onFinished = { updateCheckResult -> processUpdateCheckResult(updateCheckResult) }
            )
          }
        )

        boolean(
          title = appResources.string(R.string.settings_screen_notify_about_beta_versions),
          delegate = appSettings.notifyAboutBetaUpdates
        )

        link(
          key = "open_github_repository",
          title = appResources.string(R.string.settings_screen_open_github_repository),
          onClicked = { androidHelpers.openLink(context, githubUrl) }
        )
      }
    )

    _builtSettings[key]!!.value = builder.build()
  }

  private fun processUpdateCheckResult(updateCheckResult: UpdateManager.UpdateCheckResult) {
    when (updateCheckResult) {
      is UpdateManager.UpdateCheckResult.AlreadyOnTheLatestVersion -> {
        snackbarManager.toast(
          toastId = toastId,
          message = "Already on the latest version",
          screenKey = MainScreen.SCREEN_KEY
        )
      }
      is UpdateManager.UpdateCheckResult.Error -> {
        snackbarManager.errorToast(
          toastId = toastId,
          message = "Error: ${updateCheckResult.message}",
          screenKey = MainScreen.SCREEN_KEY
        )
      }
      UpdateManager.UpdateCheckResult.AlreadyCheckedRecently,
      UpdateManager.UpdateCheckResult.Success -> {
        // no-op
      }
    }
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

  companion object {
    private const val TAG = "AppSettingsScreenViewModel"
    private const val toastId = "update_check_result_message"
    private const val githubUrl = "https://github.com/K1rakishou/KurobaExLite"
  }

}