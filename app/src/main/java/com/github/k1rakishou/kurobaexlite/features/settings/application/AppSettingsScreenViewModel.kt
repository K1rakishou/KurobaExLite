package com.github.k1rakishou.kurobaexlite.features.settings.application

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.kpnc.KPNCScreen
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreen
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreenBuilder
import com.github.k1rakishou.kurobaexlite.features.settings.items.SettingScreens
import com.github.k1rakishou.kurobaexlite.features.settings.report.ReportIssueScreen
import com.github.k1rakishou.kurobaexlite.features.themes.ThemesScreen
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.resource.IAppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.settings.WatcherBg
import com.github.k1rakishou.kurobaexlite.helpers.settings.WatcherFg
import com.github.k1rakishou.kurobaexlite.helpers.settings.impl.RangeSetting
import com.github.k1rakishou.kurobaexlite.helpers.worker.BookmarkBackgroundWatcherWorker
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.RestartBookmarkBackgroundWatcher
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.managers.UpdateManager
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.themes.ThemeEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppSettingsScreenViewModel(
  @SuppressLint("StaticFieldLeak") private val appContext: Context,
  private val appSettings: AppSettings,
  private val androidHelpers: AndroidHelpers,
  private val appResources: IAppResources,
  private val snackbarManager: SnackbarManager,
  private val updateManager: UpdateManager,
  private val themeEngine: ThemeEngine,
  private val restartBookmarkBackgroundWatcher: RestartBookmarkBackgroundWatcher,
) : BaseViewModel() {
  private val _builtSettings = ConcurrentHashMap<SettingScreens, MutableStateFlow<SettingScreen?>>()

  private val _currentScreen = MutableStateFlow<SettingScreens>(SettingScreens.Main)
  val currentScreen: StateFlow<SettingScreens>
    get() = _currentScreen.asStateFlow()

  private val _showMenuItemsFlow = MutableSharedFlow<DisplayedMenuOptions>(extraBufferCapacity = Channel.RENDEZVOUS)
  val showMenuItemsFlow: SharedFlow<DisplayedMenuOptions>
    get() = _showMenuItemsFlow.asSharedFlow()

  private val _showDialogFlow = MutableSharedFlow<DialogScreenParameters>(extraBufferCapacity = Channel.RENDEZVOUS)
  val showDialogFlow: SharedFlow<DialogScreenParameters>
    get() = _showDialogFlow.asSharedFlow()

  private val _showSliderDialogFlow = MutableSharedFlow<SliderDialogParameters>(extraBufferCapacity = Channel.RENDEZVOUS)
  val showSliderDialogFlow: SharedFlow<SliderDialogParameters>
    get() = _showSliderDialogFlow.asSharedFlow()

  private val _showScreenFlow = MutableSharedFlow<DisplayScreenRequest>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val showScreenFlow: SharedFlow<DisplayScreenRequest>
    get() = _showScreenFlow.asSharedFlow()

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

  private suspend fun buildMainSettingsScreen() {
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
          settingNameMapper = { enum -> (enum as WatcherFg).text },
          onSettingUpdated = {
            restartBookmarkBackgroundWatcher.restart(addInitialDelay = true)
          }
        )

        enum(
          title = appResources.string(R.string.settings_screen_background_watcher_update_interval),
          delegate = appSettings.watcherIntervalBackgroundSeconds,
          filterFunc = { watcherBg ->
            if (androidHelpers.isDevFlavor()) {
              return@enum true
            }

            if (watcherBg == WatcherBg.MIN_1) {
              return@enum false
            }

            return@enum true
          },
          showOptionsScreen = { items -> displayOptionsAndWaitForSelection(items) },
          settingNameMapper = { enum -> (enum as WatcherBg).text }
        )

        boolean(
          title = appResources.string(R.string.settings_screen_reply_notifications),
          delegate = appSettings.replyNotifications
        )

        boolean(
          title = appResources.string(R.string.settings_screen_push_notifications),
          subtitleBuilder = { append(appResources.string(R.string.settings_screen_push_notifications_description)) },
          delegate = appSettings.pushNotifications,
          onSettingUpdated = {
            val pushNotificationsEnabled = appSettings.pushNotifications.read()
            if (pushNotificationsEnabled) {
              BookmarkBackgroundWatcherWorker.cancelBackgroundBookmarkWatching(
                appContext = appContext,
                flavorType = androidHelpers.getFlavorType()
              )
            } else {
              restartBookmarkBackgroundWatcher.restart(addInitialDelay = true)
            }
          }
        )

        link(
          key = "push_notifications_settings_screen",
          title = appResources.string(R.string.settings_screen_push_notifications_settings_screen),
          dependencies = listOf(appSettings.pushNotifications),
          onClicked = {
            val displayScreenRequest = DisplayScreenRequest(
              screenBuilder = { componentActivity, navigationRouter ->
                ComposeScreen.createScreen<KPNCScreen>(
                  componentActivity = componentActivity,
                  navigationRouter = navigationRouter
                )
              }
            )

            _showScreenFlow.tryEmit(displayScreenRequest)
          }
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

        slider(
          title = appResources.string(R.string.settings_screen_global_text_size_multiplier),
          delegate = appSettings.globalFontSizeMultiplier,
          showSliderDialog = { sliderDialogParameters ->
            _showSliderDialogFlow.emit(sliderDialogParameters)
            return@slider sliderDialogParameters.await()
          },
          settingDisplayFormatter = { value -> "${value}%" },
          sliderCurrentValueFormatter = { value ->
            appResources.string(R.string.settings_screen_global_text_size_multiplier_current_value, value)
          }
        )

        run {
          val currentThemeName = themeEngine.currentThemeName()

          link(
            key = "app_themes",
            title = appResources.string(R.string.settings_screen_app_themes_title),
            subtitleBuilder = { append(appResources.string(R.string.settings_screen_app_themes_subtitle, currentThemeName)) },
            onClicked = {
              val displayScreenRequest = DisplayScreenRequest(
                screenBuilder = { componentActivity, navigationRouter ->
                  ComposeScreen.createScreen<ThemesScreen>(
                    componentActivity = componentActivity,
                    navigationRouter = navigationRouter
                  )
                }
              )

              _showScreenFlow.tryEmit(displayScreenRequest)
            }
          )
        }
      }
    )
    builder.group(
      groupKey = "experimental_group",
      groupName = appResources.string(R.string.settings_screen_experimental_layout_group),
      builder = {
        string(
          title = appResources.string(R.string.settings_screen_experimental_user_agent),
          enabled = true,
          delegate = appSettings.userAgent,
          showDialogScreen = { dialogParameters ->
            val dialogScreenParams = DialogScreenParameters(dialogParameters)
            _showDialogFlow.emit(dialogScreenParams)

            dialogScreenParams.await()
          },
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
          key = "report_issue",
          title = appResources.string(R.string.settings_screen_report),
          subtitleBuilder = {
            append(appResources.string(R.string.settings_screen_report_description))
          },
          onClicked = {
            val displayScreenRequest = DisplayScreenRequest(
              screenBuilder = { componentActivity, navigationRouter ->
                ComposeScreen.createScreen<ReportIssueScreen>(
                  componentActivity = componentActivity,
                  navigationRouter = navigationRouter
                )
              }
            )

            _showScreenFlow.tryEmit(displayScreenRequest)
          }
        )

        link(
          key = "open_github_repository",
          title = appResources.string(R.string.settings_screen_open_github_repository),
          onClicked = { androidHelpers.openLink(appContext, githubUrl) }
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

  data class DialogScreenParameters(
    val dialogScreenParams: DialogScreen.Params
  ) {
    private val deferred: CompletableDeferred<Unit> = CompletableDeferred()

    fun complete(value: Unit) {
      deferred.complete(value)
    }

    suspend fun await() {
      return deferred.await()
    }
  }

  data class SliderDialogParameters(
    val title: String,
    val delegate: RangeSetting,
    val currentValueFormatter: (Int) -> String
  ) {
    private val deferred: CompletableDeferred<Int?> = CompletableDeferred()

    fun complete(value: Int?) {
      deferred.complete(value)
    }

    suspend fun await(): Int? {
      return deferred.await()
    }

  }

  class DisplayScreenRequest(
    val screenBuilder: (ComponentActivity, NavigationRouter) -> ComposeScreen
  )

  companion object {
    private const val TAG = "AppSettingsScreenViewModel"
    private const val toastId = "update_check_result_message"
    private const val githubUrl = "https://github.com/K1rakishou/KurobaExLite"
  }

}