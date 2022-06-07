package com.github.k1rakishou.kurobaexlite.features.posts.sort

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.settings.CatalogSort
import com.github.k1rakishou.kurobaexlite.helpers.settings.CatalogSortSetting
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeIcon
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeTextBarButton
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import kotlinx.coroutines.launch

class SortCatalogThreadsScreen(
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter,
  private val onApplied: () -> Unit
) : FloatingComposeScreen(componentActivity, navigationRouter) {

  override val screenKey: ScreenKey = SCREEN_KEY
  override val contentAlignment: Alignment = touchPositionDependantAlignment

  @Composable
  override fun FloatingContent() {
    val coroutineScope = rememberCoroutineScope()
    var currentCatalogSortSettingState by remember { mutableStateOf<CatalogSortSetting?>(null) }

    LaunchedEffect(
      key1 = Unit,
      block = { currentCatalogSortSettingState = appSettings.catalogSort.read() }
    )

    val currentCatalogSortSetting = if (currentCatalogSortSettingState == null) {
      return
    } else {
      currentCatalogSortSettingState!!
    }

    val width = if (globalUiInfoManager.isTablet) 360.dp else 280.dp

    Column(
      modifier = Modifier
        .padding(8.dp)
        .width(width)
        .wrapContentHeight()
    ) {
      Column(
        modifier = Modifier
          .verticalScroll(state = rememberScrollState())
      ) {
        for (catalogSort in CatalogSort.values()) {
          key(catalogSort.orderName) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .kurobaClickable(
                  onClick = {
                    val equal = currentCatalogSortSetting.sort.orderName.equals(
                      other = catalogSort.orderName,
                      ignoreCase = true
                    )

                    currentCatalogSortSettingState = if (equal) {
                      currentCatalogSortSetting.copy(ascending = !currentCatalogSortSetting.ascending)
                    } else {
                      CatalogSortSetting(sort = catalogSort, ascending = false)
                    }
                  }
                ),
              verticalAlignment = Alignment.CenterVertically
            ) {
              BuildCatalogSortOption(catalogSort, currentCatalogSortSetting)
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.End) {
        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          text = stringResource(id = R.string.close)
        ) {
          stopPresenting()
        }

        Spacer(modifier = Modifier.width(8.dp))

        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          text = stringResource(id = R.string.apply)
        ) {
          coroutineScope.launch {
            appSettings.catalogSort.write(currentCatalogSortSetting)

            onApplied()
            stopPresenting()
          }
        }
      }
    }
  }

  @Composable
  private fun RowScope.BuildCatalogSortOption(
    catalogSort: CatalogSort,
    currentCatalogSortSetting: CatalogSortSetting
  ) {
    val isThisSetting = catalogSort.orderName.equals(
      other = currentCatalogSortSetting.sort.orderName,
      ignoreCase = true
    )

    if (isThisSetting) {
      val drawableId = if (currentCatalogSortSetting.ascending) {
        R.drawable.ic_baseline_arrow_upward_24
      } else {
        R.drawable.ic_baseline_arrow_downward_24
      }

      KurobaComposeIcon(
        modifier = Modifier
          .width(32.dp)
          .height(32.dp),
        drawableId = drawableId
      )
    } else {
      Spacer(
        modifier = Modifier
          .width(32.dp)
          .height(32.dp)
      )
    }

    Spacer(modifier = Modifier.width(8.dp))

    val catalogSortText = when (catalogSort) {
      CatalogSort.BUMP -> stringResource(id = R.string.catalog_sort_order_bump)
      CatalogSort.REPLY -> stringResource(id = R.string.catalog_sort_order_thread_reply_count)
      CatalogSort.IMAGE -> stringResource(id = R.string.catalog_sort_order_thread_image_count)
      CatalogSort.CREATION_TIME -> stringResource(id = R.string.catalog_sort_order_thread_creation_time)
      CatalogSort.MODIFIED -> stringResource(id = R.string.catalog_sort_order_thread_last_modified_time)
      CatalogSort.ACTIVITY -> stringResource(id = R.string.catalog_sort_order_thread_activity)
    }

    KurobaComposeText(
      modifier = Modifier.fillMaxWidth(),
      text = catalogSortText,
      fontSize = 16.sp
    )
  }

  companion object {
    val SCREEN_KEY = ScreenKey("SortCatalogThreadsScreen")
  }
}