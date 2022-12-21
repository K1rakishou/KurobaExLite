package com.github.k1rakishou.kurobaexlite.features.boards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.KurobaComposeText
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalChanTheme
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import com.github.k1rakishou.kurobaexlite.ui.helpers.kurobaClickable
import org.koin.java.KoinJavaComponent.inject

class SiteSelectionScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  private val siteManager by inject<SiteManager>(SiteManager::class.java)

  override val screenKey: ScreenKey = SCREEN_KEY
  override val contentAlignment: Alignment = Alignment.TopCenter

  private val selectedSiteKey by requireArgument<SiteKey>(siteKeyParamKey)

  @Composable
  override fun FloatingContent() {
    super.FloatingContent()

    var sites by remember { mutableStateOf<List<SiteData>>(emptyList()) }

    LaunchedEffect(
      key1 = Unit,
      block = {
        val allSites = mutableListOf<SiteData>()

        siteManager.iterateSites { site ->
          allSites += SiteData(
            siteKey = site.siteKey,
            siteName = site.readableName,
            iconUrl = site.icon()?.toString(),
            selected = site.siteKey == selectedSiteKey
          )
        }

        sites = allSites
      }
    )
    
    LazyColumn(
      modifier = Modifier
        .wrapContentHeight()
        .widthIn(min = 256.dp),
      content = {
        items(
          count = sites.size,
          key = { index -> sites[index].siteName },
          itemContent = { index ->
            val site = sites[index]
            
            SiteContent(
              site = site,
              onSiteSelected = { siteData ->
                ScreenCallbackStorage.invokeCallback(screenKey, onSiteSelectedCallbackKey, siteData.siteKey)
                stopPresenting()
              }
            )
          }
        )    
      }
    )
  }

  @Composable
  private fun SiteContent(site: SiteData, onSiteSelected: (SiteData) -> Unit) {
    val chanTheme = LocalChanTheme.current
    val context = LocalContext.current

    Row(
      modifier = Modifier
        .height(48.dp)
        .fillMaxWidth()
        .drawBehind {
          if (site.selected) {
            drawRect(chanTheme.selectedOnBackColor)
          }
        }
        .kurobaClickable(
          bounded = true,
          onClick = { onSiteSelected(site) }
        ),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (site.iconUrl != null) {
        Spacer(modifier = Modifier.width(16.dp))

        val request = remember(key1 = site.iconUrl) {
          ImageRequest.Builder(context).data(site.iconUrl).build()
        }

        AsyncImage(
          modifier = Modifier
            .size(26.dp),
          model = request,
          contentDescription = "Site icon"
        )

        Spacer(modifier = Modifier.width(16.dp))
      }

      KurobaComposeText(
        text = site.siteName,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 20.sp
      )
    }
  }

  class SiteData(
    val siteKey: SiteKey,
    val siteName: String,
    val iconUrl: String?,
    val selected: Boolean
  )

  companion object {
    val SCREEN_KEY = ScreenKey("SiteSelectionScreen")

    val siteKeyParamKey = "site_key"

    val onSiteSelectedCallbackKey = "on_site_selected"
  }
}