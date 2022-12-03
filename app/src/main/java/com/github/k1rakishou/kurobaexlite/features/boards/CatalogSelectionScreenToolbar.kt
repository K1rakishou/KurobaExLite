package com.github.k1rakishou.kurobaexlite.features.boards

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.koinRemember
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaChildToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.presets.SimpleToolbarState
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CatalogSelectionScreenToolbar(
  private val appResources: AppResources,
  private val defaultToolbarKey: String,
  private val defaultToolbarStateKey: String
) : KurobaChildToolbar() {
  override val toolbarKey: String = defaultToolbarKey
  override val toolbarState: ToolbarState = State(defaultToolbarStateKey, appResources)

  private val catalogSelectionScreenToolbarState: State
    get() = toolbarState as State

  @Composable
  override fun Content() {
    KurobaToolbarLayout(
      leftPart = {
        catalogSelectionScreenToolbarState.leftIcon.Content(
          onClick = { key -> catalogSelectionScreenToolbarState.onIconClicked(key) }
        )
      },
      middlePart = {
        UpdateToolbarTitle()

        val context = LocalContext.current
        val siteIconUrl by catalogSelectionScreenToolbarState.siteIconUrl
        val toolbarTitle by catalogSelectionScreenToolbarState.toolbarTitleState
        val toolbarSubtitle by catalogSelectionScreenToolbarState.toolbarSubtitleState

        Row(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .kurobaClickable(onClick = { catalogSelectionScreenToolbarState.onSiteSelectorClicked() }),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Spacer(modifier = Modifier.width(8.dp))

          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (siteIconUrl != null) {
                val request = remember(key1 = siteIconUrl) {
                  ImageRequest.Builder(context).data(siteIconUrl).build()
                }

                AsyncImage(
                  modifier = Modifier.size(18.dp),
                  model = request,
                  contentDescription = null
                )
              }

              Text(
                text = toolbarTitle!!,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
              )
            }

            if (toolbarSubtitle != null) {
              Text(
                text = toolbarSubtitle!!,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
              )
            }
          }

          KurobaComposeIcon(drawableId = R.drawable.ic_baseline_arrow_drop_down_24)

          Spacer(modifier = Modifier.width(8.dp))
        }
      },
      rightPart = {
        catalogSelectionScreenToolbarState.rightIcons.fastForEach { toolbarIcon ->
          toolbarIcon.Content(onClick = { key -> catalogSelectionScreenToolbarState.onIconClicked(key) })
        }
      }
    )
  }

  @Composable
  private fun UpdateToolbarTitle() {
    val siteManager: SiteManager = koinRemember()
    val appSettings: AppSettings = koinRemember()

    fun onLastUsedSiteChanged(lastUsedSiteRaw: String) {
      val lastUsedSite = siteManager.bySiteKeyOrDefault(SiteKey(lastUsedSiteRaw))

      catalogSelectionScreenToolbarState.siteIconUrl.value = lastUsedSite.icon()?.toString()
      catalogSelectionScreenToolbarState.toolbarTitleState.value = lastUsedSite.readableName
      catalogSelectionScreenToolbarState.toolbarSubtitleState.value = appResources.string(R.string.board_selection_screen_toolbar_title)
    }

    LaunchedEffect(
      key1 = Unit,
      block = {
        val lastUsedSite = appSettings.catalogSelectionScreenLastUsedSite.read()
        onLastUsedSiteChanged(lastUsedSite)

        appSettings.catalogSelectionScreenLastUsedSite.listen()
          .collect { lastUsedSite -> onLastUsedSiteChanged(lastUsedSite) }
      }
    )
  }

  class State(
    stateKey: String,
    appResources: AppResources
  ) : SimpleToolbarState<CatalogSelectionScreen.ToolbarIcons>(
    saveableComponentKey = stateKey,
    title = appResources.string(R.string.board_selection_screen_toolbar_title),
    subtitle = null,
    _leftIcon = KurobaToolbarIcon(
      key = CatalogSelectionScreen.ToolbarIcons.Back,
      drawableId = R.drawable.ic_baseline_arrow_back_24
    ),
    _rightIcons = listOf(
      KurobaToolbarIcon(
        key = CatalogSelectionScreen.ToolbarIcons.Search,
        drawableId = R.drawable.ic_baseline_search_24
      ),
      KurobaToolbarIcon(
        key = CatalogSelectionScreen.ToolbarIcons.Refresh,
        drawableId = R.drawable.ic_baseline_refresh_24
      ),
      KurobaToolbarIcon(
        key = CatalogSelectionScreen.ToolbarIcons.SiteOptions,
        drawableId = R.drawable.ic_baseline_settings_24
      ),
      KurobaToolbarIcon(
        key = CatalogSelectionScreen.ToolbarIcons.Overflow,
        drawableId = R.drawable.ic_baseline_more_vert_24
      )
    )
  ) {
    val siteIconUrl = mutableStateOf<String?>(null)

    private val _siteSelectorClickEventFlow = MutableSharedFlow<Unit>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val siteSelectorClickEventFlow: SharedFlow<Unit>
      get() = _siteSelectorClickEventFlow.asSharedFlow()

    override fun saveState(): Bundle {
      return Bundle()
    }

    override fun restoreFromState(bundle: Bundle?) {
    }

    fun onSiteSelectorClicked() {
      _siteSelectorClickEventFlow.tryEmit(Unit)
    }

  }

}