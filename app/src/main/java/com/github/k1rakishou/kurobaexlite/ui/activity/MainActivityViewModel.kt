package com.github.k1rakishou.kurobaexlite.ui.activity

import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import org.koin.java.KoinJavaComponent.inject

class MainActivityViewModel : BaseViewModel() {
  private val loadBookmarks: LoadBookmarks by inject(LoadBookmarks::class.java)
  private val snackbarManager: SnackbarManager by inject(SnackbarManager::class.java)
  private val appResources: AppResources by inject(AppResources::class.java)

  val rootNavigationRouter = MainNavigationRouter()

  override suspend fun onViewModelReady() {
    loadBookmarks.execute(restartWork = true) { result ->
      val exception = result.exceptionOrNull()
        ?: return@execute

      snackbarManager.errorToast(
        message = appResources.string(
          R.string.main_screen_view_model_failed_to_load_bookmarks_from_database,
          exception.errorMessageOrClassName()
        ),
        screenKey = MainScreen.SCREEN_KEY
      )
    }
  }
}