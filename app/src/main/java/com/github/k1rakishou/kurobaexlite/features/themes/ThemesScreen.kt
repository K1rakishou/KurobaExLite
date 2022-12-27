package com.github.k1rakishou.kurobaexlite.features.themes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.home.HomeNavigationScreen
import com.github.k1rakishou.kurobaexlite.helpers.openChooseFileDialogSuspend
import com.github.k1rakishou.kurobaexlite.helpers.openCreateFileDialogSuspend
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.helpers.util.removeExtensionFromFileName
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarContainer
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarStateBuilder
import com.github.k1rakishou.kurobaexlite.ui.helpers.GradientBackground
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeLoadingIndicator
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeThemeDependantText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaFloatingActionButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.LazyVerticalGridWithFastScroller
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalWindowInsets
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuItem
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingMenuScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import com.github.k1rakishou.kurobaexlite.ui.helpers.modifier.KurobaComposeFadeIn
import com.github.k1rakishou.kurobaexlite.ui.helpers.rememberKurobaTextUnit
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.java.KoinJavaComponent.inject

class ThemesScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : HomeNavigationScreen<SimpleToolbar<ThemesScreen.ToolbarIcons>>(screenArgs, componentActivity, navigationRouter) {
  private val themesScreenViewModel by componentActivity.viewModel<ThemesScreenViewModel>()
  private val fileChooser by inject<FileChooser>(FileChooser::class.java)
  private val fileManager by inject<FileManager>(FileManager::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY
  override val screenContentLoadedFlow: StateFlow<Boolean> = MutableStateFlow(true)
  override val hasFab: Boolean = false

  private val defaultToolbarKey = "${screenKey.key}_toolbar"
  private val defaultToolbarStateKey = "${defaultToolbarKey}_state"

  private val defaultToolbarState by lazy {
    SimpleToolbarStateBuilder.Builder<ToolbarIcons>(componentActivity)
      .titleId(R.string.themes_screen_toolbar_title)
      .leftIcon(KurobaToolbarIcon(key = ToolbarIcons.Back, drawableId = R.drawable.ic_baseline_arrow_back_24))
      .addRightIcon(KurobaToolbarIcon(key = ToolbarIcons.Overflow, drawableId = R.drawable.ic_baseline_more_vert_24))
      .build(defaultToolbarStateKey)
  }

  override val defaultToolbar by lazy {
    SimpleToolbar(
      toolbarKey = defaultToolbarKey,
      simpleToolbarState = defaultToolbarState
    )
  }

  override val kurobaToolbarContainerState by lazy {
    kurobaToolbarContainerViewModel.getOrCreate<SimpleToolbar<ToolbarIcons>>(screenKey)
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    LaunchedEffect(
      key1 = Unit,
      block = {
        defaultToolbarState.iconClickEvents.collect { icon ->
          when (icon) {
            ToolbarIcons.Back -> { onBackPressed() }
            ToolbarIcons.Overflow -> {
              // no-op
            }
          }
        }
      }
    )

    KurobaToolbarContainer(
      toolbarContainerKey = screenKey.key,
      kurobaToolbarContainerState = kurobaToolbarContainerState,
      canProcessBackEvent = { true }
    )
  }

  @Composable
  override fun HomeNavigationScreenContent() {
    val chanTheme = LocalChanTheme.current
    val coroutineScope = rememberCoroutineScope()

    HandleBackPresses {
      if (kurobaToolbarContainerState.onBackPressed()) {
        return@HandleBackPresses true
      }

      return@HandleBackPresses popScreen()
    }

    val themes by themesScreenViewModel.themes

    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawBehind {
          val color = if (chanTheme.isDarkTheme) {
            Color.DarkGray
          } else {
            Color.LightGray
          }

          drawRect(color)
        }
    ) {
      KurobaComposeFadeIn {
        ContentInternal(
          themes = themes,
          onThemeClicked = { theme -> themesScreenViewModel.switchToTheme(theme.nameOnDisk) },
          onThemeLongClicked = { theme -> displayThemeLongClickOptions(theme) },
          onImportFromFileButtonClicked = { coroutineScope.launch { importThemeFromFile() } },
          onImportFromClipboardButtonClicked = { coroutineScope.launch { importThemeFromClipboard() } },
        )
      }
    }
  }

  private suspend fun importThemeFromClipboard() {
    val clipboardContent = androidHelpers.clipboardContent()
    if (clipboardContent.isNullOrEmpty()) {
      return
    }

    val themeFileName = requestUserThemeFileName(prevUsedThemeFileName = null)
    if (themeFileName.isNullOrEmpty()) {
      return
    }

    val result = themesScreenViewModel.importThemeFromClipboard(
      themeJson = clipboardContent,
      themeFileName = themeFileName
    ).toastOnError()

    val success = if (result.isFailure) {
      return
    } else {
      result.getOrThrow()
    }

    if (success) {
      snackbarManager.toast(R.string.themes_screen_theme_import_success)
    } else {
      snackbarManager.errorToast(R.string.themes_screen_theme_import_error)
    }
  }

  private suspend fun importThemeFromFile() {
    val inputFileUri = fileChooser.openChooseFileDialogSuspend()
      .toastOnError()
      .getOrNull()
      ?: return

    val fileName = fileManager.fromUri(inputFileUri)
      ?.let { file ->
        return@let fileManager.getName(file)
          .removeExtensionFromFileName()
          .filter { ch -> ch.isLetterOrDigit() || ch == '_' }
      }
      .takeIf { it.isNotNullNorBlank() }

    val themeFileName = requestUserThemeFileName(prevUsedThemeFileName = fileName)
    if (themeFileName.isNullOrEmpty()) {
      return
    }

    val result = themesScreenViewModel.importThemeFromFile(
      inputFileUri = inputFileUri,
      themeFileName = themeFileName
    ).toastOnError()

    val success = if (result.isFailure) {
      return
    } else {
      result.getOrThrow()
    }

    if (success) {
      snackbarManager.toast(R.string.themes_screen_theme_import_success)
    } else {
      snackbarManager.errorToast(R.string.themes_screen_theme_import_error)
    }
  }

  private suspend fun requestUserThemeFileName(prevUsedThemeFileName: String?): String? {
    val (canceled, themeFileName) = suspendCancellableCoroutine<Pair<Boolean, String?>> { cancellableContinuation ->
      val fileNameToUse = prevUsedThemeFileName?.removeExtensionFromFileName() ?: "theme_file"

      navigationRouter.presentScreen(
        DialogScreen(
          componentActivity = componentActivity,
          navigationRouter = navigationRouter,
          params = DialogScreen.Params(
            title = DialogScreen.Text.Id(R.string.themes_screen_enter_output_theme_file_name_title),
            description = DialogScreen.Text.Id(R.string.themes_screen_enter_output_theme_file_name_description),
            inputs = listOf(
              DialogScreen.Input.String(
                hint = DialogScreen.Text.Id(R.string.themes_screen_enter_output_theme_file_name_hint),
                initialValue = fileNameToUse
              )
            ),
            negativeButton = DialogScreen.DialogButton(
              buttonText = R.string.cancel,
              onClick = { cancellableContinuation.resumeSafe(true to null) }
            ),
            positiveButton = DialogScreen.PositiveDialogButton(
              buttonText = R.string.import_str,
              isActionDangerous = true,
              onClick = { results ->
                val themeFileName = results.firstOrNull()?.takeIf { it.isNotNullNorBlank() }?.lowercase()
                cancellableContinuation.resumeSafe(false to themeFileName)
              }
            )
          ),
          onDismissed = { cancellableContinuation.resumeSafe(true to null) }
        )
      )
    }

    if (canceled) {
      return null
    }

    if (themeFileName.isNullOrBlank()) {
      snackbarManager.errorToast(R.string.themes_screen_enter_output_theme_file_name_null_or_blank)
      delay(250L)
      return requestUserThemeFileName(prevUsedThemeFileName = null)
    }

    val allSymbolsAreValid = themeFileName.all { char -> char.isLetterOrDigit() || char == '_' }
    if (!allSymbolsAreValid) {
      snackbarManager.errorToast(R.string.themes_screen_enter_output_theme_file_name_bad_symbols)
      delay(250L)
      return requestUserThemeFileName(prevUsedThemeFileName = themeFileName)
    }

    val result = themesScreenViewModel.themeWithNameAlreadyExists(themeFileName)
      .toastOnError()

    val themeAlreadyExists = if (result.isFailure) {
      return null
    } else {
      result.getOrThrow()
    }

    if (themeAlreadyExists) {
      val errorMessage = appResources.string(R.string.themes_screen_enter_output_theme_file_name_already_exists, themeFileName)
      snackbarManager.errorToast(errorMessage)
      delay(250L)
      return requestUserThemeFileName(prevUsedThemeFileName = themeFileName)
    }

    return "${themeFileName}.json"
  }

  private fun displayThemeLongClickOptions(theme: ThemesScreenViewModel.ThemeUi) {
    val floatingMenuItems = mutableListOf<FloatingMenuItem>()

    floatingMenuItems += FloatingMenuItem.Text(
      menuItemKey = ACTION_EXPORT_THEME_AS_JSON,
      text = FloatingMenuItem.MenuItemText.Id(R.string.themes_screen_export_theme_json),
    )

    floatingMenuItems += FloatingMenuItem.Text(
      menuItemKey = ACTION_COPY_THEME_JSON_TO_CLIPBOARD,
      text = FloatingMenuItem.MenuItemText.Id(R.string.themes_screen_copy_theme_json),
    )

    if (!themesScreenViewModel.isDefaultTheme(theme.nameOnDisk)) {
      floatingMenuItems += FloatingMenuItem.Text(
        menuItemKey = ACTION_DELETE_THEME,
        text = FloatingMenuItem.MenuItemText.Id(R.string.themes_screen_delete_theme),
      )
    }

    navigationRouter.presentScreen(
      FloatingMenuScreen(
        floatingMenuKey = FloatingMenuScreen.THEMES_SCREEN_THEME_LONG_CLICK_OPTIONS,
        componentActivity = componentActivity,
        navigationRouter = navigationRouter,
        menuItems = floatingMenuItems,
        onMenuItemClicked = { menuItem ->
          screenCoroutineScope.launch {
            when (menuItem.menuItemKey as Int) {
              ACTION_EXPORT_THEME_AS_JSON -> {
                val fileName = "${theme.chanTheme.name}.json"
                val fileUri = fileChooser.openCreateFileDialogSuspend(fileName).getOrNull()
                  ?: return@launch

                val success = themesScreenViewModel.exportThemeToFile(theme.chanTheme, fileUri)
                  .toastOnError()
                  .getOrNull()
                  ?: return@launch

                if (success) {
                  snackbarManager.toast(R.string.themes_screen_theme_export_success)
                } else {
                  snackbarManager.errorToast(R.string.themes_screen_theme_export_error)
                }
              }
              ACTION_COPY_THEME_JSON_TO_CLIPBOARD -> {
                val json = themesScreenViewModel.convertThemeToJsonString(theme.chanTheme)
                  .toastOnError()
                  .getOrNull()
                  ?: return@launch

                androidHelpers.copyToClipboard("Theme json", json)
                snackbarManager.toast(R.string.themes_screen_copy_theme_json_success)
              }
              ACTION_DELETE_THEME -> {
                val success = themesScreenViewModel.deleteTheme(theme.nameOnDisk)
                  .toastOnError()
                  .getOrNull()
                  ?: return@launch

                if (success) {
                  snackbarManager.toast(R.string.themes_screen_delete_theme_success)
                } else {
                  snackbarManager.errorToast(R.string.themes_screen_delete_theme_error)
                }
              }
            }
          }
        }
      )
    )
  }

  enum class ToolbarIcons {
    Back,
    Overflow
  }

  companion object {
    val SCREEN_KEY = ScreenKey("ThemesScreen")

    private const val ACTION_EXPORT_THEME_AS_JSON = 0
    private const val ACTION_COPY_THEME_JSON_TO_CLIPBOARD = 1
    private const val ACTION_DELETE_THEME = 2
  }

}

@Composable
private fun ContentInternal(
  themes: ImmutableList<ThemesScreenViewModel.ThemeUi>?,
  onThemeClicked: (ThemesScreenViewModel.ThemeUi) -> Unit,
  onThemeLongClicked: (ThemesScreenViewModel.ThemeUi) -> Unit,
  onImportFromFileButtonClicked: () -> Unit,
  onImportFromClipboardButtonClicked: () -> Unit
) {
  val windowInsets = LocalWindowInsets.current
  val chanTheme = LocalChanTheme.current
  val toolbarHeight = dimensionResource(id = R.dimen.toolbar_height)

  val appSetting = koinRemember<AppSettings>()
  val themeEngine = koinRemember<ThemeEngine>()

  var selectedLightTheme by remember { mutableStateOf<String?>(null) }
  var selectedDarkTheme by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(
    key1 = Unit,
    block = {
      selectedLightTheme = appSetting.currentLightThemeName.read()
        .takeIf { it.isNotBlank() }
        ?: themeEngine.defaultDarkTheme.name

      appSetting.currentLightThemeName.listen(eagerly = false)
        .collectLatest { currentLightThemeName -> selectedLightTheme = currentLightThemeName }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      selectedDarkTheme = appSetting.currentDarkThemeName.read()
        .takeIf { it.isNotBlank() }
        ?: themeEngine.defaultLightTheme.name

      appSetting.currentDarkThemeName.listen(eagerly = false)
        .collectLatest { currentLightThemeName -> selectedDarkTheme = currentLightThemeName }
    }
  )

  Box(modifier = Modifier.fillMaxSize()) {
    if (themes == null) {
      KurobaComposeLoadingIndicator()
    } else {
      val columns = GridCells.Fixed(2)
      val lazyGridState = rememberLazyGridState()

      val contentPadding = remember(key1 = windowInsets) {
        windowInsets.copyInsets(
          newLeft = 0.dp,
          newRight = 0.dp,
          newTop = windowInsets.top + toolbarHeight
        ).asPaddingValues()
      }

      LazyVerticalGridWithFastScroller(
        columns = columns,
        lazyGridState = lazyGridState,
        contentPadding = contentPadding,
        content = {
          items(
            count = themes.size,
            key = { index -> themes[index].nameOnDisk },
            contentType = { "theme_preview" },
            itemContent = { index ->
              ThemePreview(
                selectionColor = chanTheme.accentColor,
                selectedLightTheme = selectedLightTheme,
                selectedDarkTheme = selectedDarkTheme,
                themeUi = themes[index],
                onThemeClicked = onThemeClicked,
                onThemeLongClicked = onThemeLongClicked
              )
            }
          )

          item(
            key = "Add new theme button",
            contentType = "add_new_theme_button",
            content = {
              AddNewThemeButton(
                onImportFromFileButtonClicked = onImportFromFileButtonClicked,
                onImportFromClipboardButtonClicked = onImportFromClipboardButtonClicked
              )
            }
          )
        }
      )
    }
  }
}

@Composable
private fun AddNewThemeButton(
  onImportFromFileButtonClicked: () -> Unit,
  onImportFromClipboardButtonClicked: () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val stroke = remember {
    with(density) {
      val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
      Stroke(width = 2.dp.toPx(), pathEffect = pathEffect)
    }
  }

  val cornerRadius = remember {
    with(density) {
      CornerRadius(4.dp.toPx(), 4.dp.toPx())
    }
  }

  val borderColor = remember { Color(0xFF3371b0) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(256.dp)
      .padding(2.dp)
      .padding(horizontal = 2.dp, vertical = 2.dp)
      .drawBehind {
        drawRect(chanTheme.backColor)

        drawRoundRect(
          color = borderColor,
          cornerRadius = cornerRadius,
          style = stroke,
        )
      },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    KurobaComposeTextBarButton(
      text = stringResource(id = R.string.themes_screen_import_theme_from_file),
      fontSize = 12.sp,
      onClick = onImportFromFileButtonClicked
    )

    Spacer(modifier = Modifier.height(16.dp))

    KurobaComposeTextBarButton(
      text = stringResource(id = R.string.themes_screen_import_theme_from_clipboard),
      fontSize = 12.sp,
      onClick = onImportFromClipboardButtonClicked
    )
  }
}

@Composable
private fun ThemePreview(
  selectionColor: Color,
  selectedLightTheme: String?,
  selectedDarkTheme: String?,
  themeUi: ThemesScreenViewModel.ThemeUi,
  onThemeClicked: (ThemesScreenViewModel.ThemeUi) -> Unit,
  onThemeLongClicked: (ThemesScreenViewModel.ThemeUi) -> Unit,
) {
  val selected = remember(key1 = selectedLightTheme, key2 = selectedDarkTheme) {
    themeUi.nameOnDisk == selectedLightTheme || themeUi.nameOnDisk == selectedDarkTheme
  }

  CompositionLocalProvider(LocalChanTheme provides themeUi.chanTheme) {
    val chanTheme = LocalChanTheme.current

    SelectionWrapper(selected = selected, selectionColor = selectionColor) {
      GradientBackground(
        modifier = Modifier
          .fillMaxWidth()
          .height(256.dp)
          .kurobaClickable(
            bounded = true,
            onClick = { onThemeClicked(themeUi) },
            onLongClick = { onThemeLongClicked(themeUi) }
          )
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .align(Alignment.TopCenter)
            .drawBehind { drawRect(chanTheme.backColor) },
          contentAlignment = Alignment.Center
        ) {
          val darkTypeThemeName = stringResource(id = R.string.themes_screen_dark_theme_type_name)
          val lightTypeThemeName = stringResource(id = R.string.themes_screen_light_theme_type_name)

          val chanThemeNameWithType = remember(chanTheme) {
            buildString {
              append(chanTheme.name)
              append(" ")

              if (chanTheme.isDarkTheme) {
                append("(${darkTypeThemeName})")
              } else {
                append("(${lightTypeThemeName})")
              }
            }
          }

          KurobaComposeThemeDependantText(
            text = chanThemeNameWithType,
            fontSize = rememberKurobaTextUnit(fontSize = 14.sp, min = 14.sp, max = 14.sp)
          )
        }

        if (selected) {
          Box(
            modifier = Modifier
              .size(22.dp)
              .align(Alignment.TopEnd)
              .offset { IntOffset(x = -(8.dp.roundToPx()), y = 8.dp.roundToPx()) }
              .drawBehind { drawCircle(selectionColor) },
            contentAlignment = Alignment.Center
          ) {
            KurobaComposeIcon(
              modifier = Modifier
                .size(18.dp),
              iconColor = Color.White,
              drawableId = R.drawable.ic_selection_checkmark_no_bg
            )
          }
        }

        KurobaFloatingActionButton(
          modifier = Modifier
            .size(42.dp)
            .align(Alignment.BottomEnd)
            .offset { IntOffset(x = -(8.dp.roundToPx()), y = -(8.dp.roundToPx())) },
          iconDrawableId = R.drawable.ic_baseline_create_24,
          onClick = { }
        )
      }
    }
  }
}

@Composable
private fun SelectionWrapper(
  selected: Boolean,
  selectionColor: Color,
  content: @Composable () -> Unit
) {
  val contentMovable = remember { movableContentOf(content) }

  if (!selected) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
      contentMovable()
    }
  } else {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(2.dp)
        .border(width = 2.dp, color = selectionColor, shape = RoundedCornerShape(4.dp))
        .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
      contentMovable()
    }
  }
}