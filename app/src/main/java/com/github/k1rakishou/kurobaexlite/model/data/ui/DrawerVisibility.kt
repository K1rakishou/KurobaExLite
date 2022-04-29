package com.github.k1rakishou.kurobaexlite.model.data.ui

sealed class DrawerVisibility {
  val isOpened: Boolean
    get() {
      return when (this) {
        Closed,
        Closing -> false
        is Drag -> progress > 0.5f
        Opened,
        Opening -> true
      }
    }

  data class Drag(
    val isDragging: Boolean,
    val progress: Float,
    val velocity: Float
  ) : DrawerVisibility() {
    val progressInverted: Float
      get() = 1f - progress

    override fun toString(): String = "DrawerVisibility.Drag($isDragging, $progress, $velocity)"
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