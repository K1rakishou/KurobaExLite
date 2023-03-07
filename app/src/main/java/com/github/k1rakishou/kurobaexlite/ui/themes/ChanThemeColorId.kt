package com.github.k1rakishou.kurobaexlite.ui.themes

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class ChanThemeColorId(val id: Int) : Parcelable {
  PostSubject(0),
  PostName(1),
  Accent(2),
  PostInlineQuote(3),
  PostQuote(4),
  BackColor(5),
  PostLink(6),
  TextColor(7);

  companion object {
    fun byId(id: Int): ChanThemeColorId {
      return values()
        .firstOrNull { chanThemeColorId -> chanThemeColorId.id == id }
        ?: throw IllegalAccessException("Failed to find color by id: $id")
    }

    fun byName(name: String): ChanThemeColorId? {
      return values()
        .firstOrNull { chanThemeColorId -> chanThemeColorId.name.equals(other = name, ignoreCase = true) }
    }
  }
}