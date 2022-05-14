package com.github.k1rakishou.kurobaexlite.helpers

import android.os.Bundle

interface SaveableComponent {
  val saveableComponentKey: String
  fun saveState(): Bundle
  fun restoreFromState(bundle: Bundle?)
}