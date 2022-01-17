package com.github.k1rakishou.kurobaexlite

import android.app.Application
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

class KurobaExLiteApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    startKoin {
      modules(provideModules())
    }
  }

  private fun KoinApplication.provideModules(): List<Module> {
    val modules = mutableListOf<Module>()

    modules += module {
      single {
        Navigation()
      }
    }

    return modules
  }

}