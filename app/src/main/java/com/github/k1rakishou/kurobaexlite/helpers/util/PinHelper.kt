package com.github.k1rakishou.kurobaexlite.helpers.util

object PinHelper {

  fun getShortUnreadCount(value: Int): String {
    if (value < 1000) {
      return value.toString()
    }

    val thousands = value.toFloat() / 1000f
    if (thousands >= 999f) {
      return "999k+"
    }

    return "%.${1}f".format(thousands) + "k"
  }

}