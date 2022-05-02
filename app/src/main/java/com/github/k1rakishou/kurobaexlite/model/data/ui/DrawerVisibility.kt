package com.github.k1rakishou.kurobaexlite.model.data.ui

import androidx.compose.ui.unit.Velocity

sealed class DrawerVisibility {
  val isOpened: Boolean
    get() {
      return when (this) {
        Closed,
        Closing,
        is Fling,
        is Drag -> false
        Opened,
        Opening -> true
      }
    }

  data class Drag(
    val time: Long,
    val isDragging: Boolean,
    val dragX: Float
  ) : DrawerVisibility() {
    override fun toString(): String = "DrawerVisibility.Drag($isDragging, $dragX)"
  }

  data class Fling(val velocity: Velocity) : DrawerVisibility() {
    override fun toString(): String = "DrawerVisibility.Fling($velocity)"
  }

  object Opening : DrawerVisibility() {
    override fun toString(): String = "DrawerVisibility.Opening()"
  }
  object Opened : DrawerVisibility() {
    override fun toString(): String = "DrawerVisibility.Opened()"
  }
  object Closing : DrawerVisibility() {
    override fun toString(): String = "DrawerVisibility.Closing()"
  }
  object Closed : DrawerVisibility() {
    override fun toString(): String = "DrawerVisibility.Closed()"
  }
}