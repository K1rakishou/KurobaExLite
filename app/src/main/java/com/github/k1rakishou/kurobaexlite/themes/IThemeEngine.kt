package com.github.k1rakishou.kurobaexlite.themes

interface IThemeEngine {
  val chanTheme: ChanTheme

  fun toggleTheme()
  fun switchToDefaultTheme(darkThemeWasUsed: Boolean)
  fun switchToTheme(nameOnDisk: String)
}