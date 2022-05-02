package com.github.k1rakishou.kurobaexlite.model.data.ui

sealed class DrawerVisibility {
  val isOpened: Boolean
    get() {
      return when (this) {
        Closed,
        Closing,
        is Drag -> false
        Opened,
        Opening -> true
      }
    }

  data class Drag(
    val isDragging: Boolean,
    val dragX: Float
  ) : DrawerVisibility() {
    override fun toString(): String = "DrawerVisibility.Drag($isDragging, $dragX)"
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