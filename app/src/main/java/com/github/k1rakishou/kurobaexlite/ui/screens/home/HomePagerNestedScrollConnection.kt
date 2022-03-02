package com.github.k1rakishou.kurobaexlite.ui.screens.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

class HomePagerNestedScrollConnection(
  private val drawerWidth: Float,
  private val currentPagerPage: () -> Int,
  private val shouldConsumeAllScrollEvents: () -> Boolean,
  private val onDragging: (Boolean, Float, Float) -> Unit
) : NestedScrollConnection {
  private var scrolled = 0f
  private var pointerDown = false

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    pointerDown = true

    if (shouldConsumeAllScrollEvents()) {
      return available
    }

    if (available.x < 0f && scrolled > 0f && currentPagerPage() == 0) {
      dragDrawerLayout(available)
      return available
    }

    return super.onPreScroll(available, source)
  }

  override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource
  ): Offset {
    if (available.x > 0f && pointerDown && currentPagerPage() == 0) {
      dragDrawerLayout(available)
      return available
    }

    return super.onPostScroll(consumed, available, source)
  }

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
    if (scrolled > 0f) {
      val dragProgress = (scrolled / drawerWidth).coerceIn(0f, 1f)
      onDragging(false, dragProgress, available.x)
    }

    scrolled = 0f
    pointerDown = false

    return super.onPostFling(consumed, available)
  }

  private fun dragDrawerLayout(available: Offset) {
    scrolled += available.x

    val dragProgress = (scrolled / drawerWidth).coerceIn(0f, 1f)
    onDragging(true, dragProgress, 0f)
  }
}