package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.sites.ResolvedDescriptor
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.ToolbarMenuItem
import com.github.k1rakishou.kurobaexlite.ui.screens.helpers.dialog.DialogScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.HomeScreenViewModel
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreen
import com.github.k1rakishou.kurobaexlite.ui.screens.posts.thread.ThreadScreenViewModel
import logcat.logcat

class CatalogScreenToolbarActionHandler(
  private val componentActivity: ComponentActivity,
  private val navigationRouter: NavigationRouter,
  private val catalogScreenViewModel: CatalogScreenViewModel,
  private val threadScreenViewModel: ThreadScreenViewModel,
  private val homeScreenViewModel: HomeScreenViewModel,
) {

  fun processClickedToolbarMenuItem(menuItem: ToolbarMenuItem) {
    logcat { "catalog processClickedToolbarMenuItem id=${menuItem.menuItemId}" }

    when (menuItem.menuItemId) {
      ACTION_RELOAD -> catalogScreenViewModel.reload()
      ACTION_OPEN_THREAD_BY_IDENTIFIER -> {
        navigationRouter.presentScreen(
          DialogScreen(
            componentActivity = componentActivity,
            navigationRouter = navigationRouter,
            params = DialogScreen.Params(
              title = R.string.catalog_screen_enter_identifier,
              description = R.string.catalog_screen_enter_identifier_description,
              input = DialogScreen.Input.String(),
              negativeButton = DialogScreen.DialogButton(buttonText = R.string.cancel),
              positiveButton = DialogScreen.PositiveDialogButton(
                buttonText = R.string.ok,
                onClick = { value ->
                  if (value.isNullOrEmpty()) {
                    return@PositiveDialogButton
                  }

                  val resolvedDescriptor = homeScreenViewModel.resolveDescriptorFromRawIdentifier(value)
                  if (resolvedDescriptor == null) {
                    homeScreenViewModel.toast("Failed to parse \'$value'\"")
                    return@PositiveDialogButton
                  }

                  // TODO(KurobaEx): come up with a better solution than doing it manually
                  homeScreenViewModel.updateCurrentPage(screenKey = ThreadScreen.SCREEN_KEY)

                  when (resolvedDescriptor) {
                    is ResolvedDescriptor.CatalogOrThread -> {
                      when (resolvedDescriptor.chanDescriptor) {
                        is CatalogDescriptor -> {
                          catalogScreenViewModel.loadCatalog(resolvedDescriptor.chanDescriptor)
                        }
                        is ThreadDescriptor -> {
                          threadScreenViewModel.loadThread(resolvedDescriptor.chanDescriptor)
                        }
                      }
                    }
                    is ResolvedDescriptor.Post -> {
                      threadScreenViewModel.loadThread(resolvedDescriptor.postDescriptor.threadDescriptor)
                      // TODO(KurobaEx): do something with the postNo (mark it or something)
                    }
                  }
                }
              )
            )
          )
        )
      }
    }
  }

  companion object {
    const val ACTION_OPEN_THREAD_BY_IDENTIFIER = 0
    const val ACTION_RELOAD = 1
  }

}