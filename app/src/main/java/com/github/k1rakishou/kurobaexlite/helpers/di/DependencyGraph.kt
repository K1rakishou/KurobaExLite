package com.github.k1rakishou.kurobaexlite.helpers.di

import com.github.k1rakishou.kurobaexlite.BuildConfig
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module

object DependencyGraph {

  fun initialize(
    application: KurobaExLiteApplication,
    appCoroutineScope: CoroutineScope
  ): List<Module> {
    val modules = mutableListOf<Module>()

    // Create everything eagerly to check for cycle dependencies when using dev builds
    val createAtStart = BuildConfig.FLAVOR_TYPE == AndroidHelpers.FlavorType.Development.rawType

    modules += module(createdAtStart = createAtStart) {
      system(application, appCoroutineScope)
      misc()
      model()
      interactors()
      managers()
      viewModels()
    }

    return modules
  }

}