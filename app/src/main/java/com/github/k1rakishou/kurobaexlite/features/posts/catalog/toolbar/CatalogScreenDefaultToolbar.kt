package com.github.k1rakishou.kurobaexlite.features.posts.catalog.toolbar

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
import androidx.compose.runtime.collectAsState
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
import com.github.k1rakishou.kurobaexlite.features.posts.catalog.CatalogScreenViewModel
import com.github.k1rakishou.kurobaexlite.features.posts.shared.toolbar.PostsScreenDefaultToolbar
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarIcon
import com.github.k1rakishou.kurobaexlite.ui.elements.toolbar.KurobaToolbarLayout
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CatalogScreenDefaultToolbar(
  private val catalogScreenViewModel: CatalogScreenViewModel,
  private val onBackPressed: suspend () -> Unit,
  private val showCatalogSelectionScreen: () -> Unit,
  private val showSortCatalogThreadsScreen: () -> Unit,
  private val showLocalSearchToolbar: () -> Unit,
  private val showOverflowMenu: () -> Unit,
) : PostsScreenDefaultToolbar<CatalogScreenDefaultToolbar.State>() {
  private val key = "CatalogScreenDefaultToolbar"
  private val state: State = State("${key}_state")

  override val toolbarKey: String = key
  override val toolbarState: ToolbarState = state

  @Composable
  override fun Content() {
    val screenContentLoaded by catalogScreenViewModel.postScreenState.contentLoaded.collectAsState()

    LaunchedEffect(
      key1 = screenContentLoaded,
      block = {
        state.rightIcons.forEach { toolbarIcon ->
          if (toolbarIcon.key == State.Icons.Overflow) {
            return@forEach
          }

          toolbarIcon.enabled.value = screenContentLoaded
        }
      }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        state.selectCatalogClickEvents.collect {
          showCatalogSelectionScreen()
        }
      }
    )

    UpdateToolbarTitle(
      isCatalogMode = true,
      postScreenState = catalogScreenViewModel.postScreenState,
      defaultToolbarState = { state }
    )

    LaunchedEffect(
      key1 = Unit,
      block = {
        state.iconClickEvents.collect { icon ->
          when (icon) {
            State.Icons.Drawer -> {
              onBackPressed()
            }
            State.Icons.Search -> {
              showLocalSearchToolbar()
            }
            State.Icons.Sort -> {
              showSortCatalogThreadsScreen()
            }
            State.Icons.Overflow -> {
              showOverflowMenu()
            }
          }
        }
      }
    )

    KurobaToolbarLayout(
      leftPart = {
        state.leftIcon.Content(onClick = { key -> state.onIconClicked(key) })
      },
      middlePart = {
        val context = LocalContext.current

        val toolbarTitle by state.toolbarTitleState
        val toolbarSubtitle by state.toolbarSubtitleState
        val siteIconUrl by state.siteIconUrl
        val showClickableMenuIcon by state.showClickableMenuIcon
        val displayMainContent by state.displayMainContent

        if (toolbarTitle != null) {
          Row(
            modifier = Modifier
              .fillMaxHeight()
              .fillMaxWidth()
              .kurobaClickable(onClick = { state.onSelectCatalogClicked() }),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (displayMainContent) {
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
                      contentDescription = "Site icon"
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

              if (showClickableMenuIcon) {
                KurobaComposeIcon(drawableId = R.drawable.ic_baseline_arrow_drop_down_24)

                Spacer(modifier = Modifier.width(8.dp))
              }
            } else {
              Row {
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                  text = toolbarTitle!!,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  fontSize = 16.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                if (showClickableMenuIcon) {
                  KurobaComposeIcon(drawableId = R.drawable.ic_baseline_arrow_drop_down_24)

                  Spacer(modifier = Modifier.width(8.dp))
                }
              }
            }
          }
        }
      },
      rightPart = {
        state.rightIcons.fastForEach { toolbarIcon ->
          toolbarIcon.Content(onClick = { key -> state.onIconClicked(key) })
        }
      }
    )
  }

  class State(
    override val saveableComponentKey: String
  ) : PostsScreenDefaultToolbar.PostsScreenToolbarState() {
    val siteIconUrl = mutableStateOf<String?>(null)
    val showClickableMenuIcon = mutableStateOf(true)

    val leftIcon = KurobaToolbarIcon(
      key = Icons.Drawer,
      drawableId = R.drawable.ic_baseline_dehaze_24
    )

    val rightIcons = listOf(
      KurobaToolbarIcon(
        key = Icons.Search,
        drawableId = R.drawable.ic_baseline_search_24,
        enabled = false
      ),
      KurobaToolbarIcon(
        key = Icons.Sort,
        drawableId = R.drawable.ic_baseline_sort_24,
        enabled = false
      ),
      KurobaToolbarIcon(
        key = Icons.Overflow,
        drawableId = R.drawable.ic_baseline_more_vert_24,
      ),
    )

    private val _iconClickEvents = MutableSharedFlow<Icons>(extraBufferCapacity = Channel.UNLIMITED)
    val iconClickEvents: SharedFlow<Icons>
      get() = _iconClickEvents.asSharedFlow()

    private val _selectCatalogClickEvents = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.UNLIMITED)
    val selectCatalogClickEvents: SharedFlow<Unit>
      get() = _selectCatalogClickEvents.asSharedFlow()

    override fun saveState(): Bundle {
      val bundle = Bundle()
      bundle.putString(TITLE_KEY, toolbarTitleState.value)
      bundle.putString(SUBTITLE_KEY, toolbarSubtitleState.value)
      bundle.putString(SITE_ICON_KEY, siteIconUrl.value)
      return bundle
    }

    override fun restoreFromState(bundle: Bundle?) {
      bundle?.getString(TITLE_KEY)?.let { title -> toolbarTitleState.value = title }
      bundle?.getString(SUBTITLE_KEY)?.let { subtitle -> toolbarSubtitleState.value = subtitle }
      bundle?.getString(SITE_ICON_KEY)?.let { siteIcon -> siteIconUrl.value = siteIcon }
    }

    fun findIconByKey(key: Icons) : KurobaToolbarIcon<Icons>? {
      if (leftIcon.key == key) {
        return leftIcon
      }

      return rightIcons.firstOrNull { icon -> icon.key == key }
    }

    fun onIconClicked(icons: Icons) {
      _iconClickEvents.tryEmit(icons)
    }

    fun onSelectCatalogClicked() {
      _selectCatalogClickEvents.tryEmit(Unit)
    }

    enum class Icons {
      Drawer,
      Search,
      Sort,
      Overflow
    }

    companion object {
      private const val TITLE_KEY = "title"
      private const val SUBTITLE_KEY = "subtitle"
      private const val SITE_ICON_KEY = "site_icon"
    }
  }

}