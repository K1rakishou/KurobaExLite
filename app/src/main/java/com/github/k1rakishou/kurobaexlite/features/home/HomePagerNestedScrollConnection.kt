package com.github.k1rakishou.kurobaexlite.features.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

class HomePagerNestedScrollConnection(
  private val currentPagerPage: () -> Int,
  private val isGestureCurrentlyAllowed: () -> Boolean,
  private val shouldConsumeAllScrollEvents: () -> Boolean,
  private val onDragging: (dragging: Boolean, current: Float) -> Unit
) : NestedScrollConnection {
  private var scrolled = 0f
  private var pointerDown = false

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    pointerDown = true

    if (shouldConsumeAllScrollEvents()) {
      return available
    }

    if (!isGestureCurrentlyAllowed()) {
      return available.copy(y = 0f)
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
      onDragging(false, scrolled)
    }

    scrolled = 0f
    pointerDown = false

    return super.onPostFling(consumed, available)
  }

  private fun dragDrawerLayout(available: Offset) {
    if (!isGestureCurrentlyAllowed()) {
      return
    }

    scrolled += available.x
    onDragging(true, scrolled)
  }
}