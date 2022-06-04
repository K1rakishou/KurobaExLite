package com.github.k1rakishou.kurobaexlite.features.home.pages

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.github.k1rakishou.kurobaexlite.navigation.RouterHost
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ComposeScreenWithToolbar
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

class SinglePage private constructor(
  override val childScreens: List<ChildScreen<ComposeScreenWithToolbar>>
) : AbstractPage<ComposeScreenWithToolbar>() {

  val screen: ChildScreen<ComposeScreenWithToolbar>
    get() = childScreens.first()

  override fun screenKey(): ScreenKey {
    return screen.composeScreen.screenKey
  }

  override fun hasScreen(screenKey: ScreenKey): Boolean {
    return childScreens
      .any { childScreen -> childScreen.composeScreen.screenKey == screenKey }
  }

  override fun screenHasChildren(screenKey: ScreenKey): Boolean {
    val childScreen = childScreens
      .firstOrNull { childScreen -> childScreen.composeScreen.screenKey == screenKey }
      ?.composeScreen
      ?: return false

    return childScreen.hasChildScreens()
  }

  override fun canDragPager(): Boolean {
    return childScreens.any { childScreen -> childScreen.composeScreen.topChildScreen().canDragPager() }
  }

  @Composable
  override fun Toolbar(boxScope: BoxScope) {
    screen.composeScreen.topChildScreen().Toolbar(boxScope)
  }

  @Composable
  override fun Content() {
    RouterHost(
      navigationRouter = screen.composeScreen.navigationRouter,
      defaultScreenFunc = { screen.composeScreen }
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as SinglePage

    if (screen.screenKey != other.screen.screenKey) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + screen.screenKey.hashCode()
    return result
  }

  companion object {
    fun of(composeScreen: ComposeScreenWithToolbar): SinglePage {
      return SinglePage(listOf(ChildScreen(composeScreen)))
    }
  }

}