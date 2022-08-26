package com.github.k1rakishou.kurobaexlite.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.features.main.MainScreen
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.interactors.bookmark.LoadBookmarks
import com.github.k1rakishou.kurobaexlite.managers.SnackbarManager
import com.github.k1rakishou.kurobaexlite.navigation.MainNavigationRouter
import kotlinx.coroutines.launch

class MainActivityViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val loadBookmarks: LoadBookmarks,
  private val snackbarManager: SnackbarManager,
  private val appResources: AppResources,
) : BaseViewModel() {
  val rootNavigationRouter = MainNavigationRouter()

  override suspend fun onViewModelReady() {
    super.onViewModelReady()

    viewModelScope.launch {
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
}