package com.github.k1rakishou.kpnc

import android.content.Context
import com.github.k1rakishou.kpnc.domain.GoogleServicesChecker
import com.github.k1rakishou.kpnc.domain.GoogleServicesCheckerImpl
import com.github.k1rakishou.kpnc.domain.KPNCFirebaseServiceDelegate
import com.github.k1rakishou.kpnc.domain.KPNCFirebaseServiceDelegateImpl
import com.github.k1rakishou.kpnc.domain.MessageReceiver
import com.github.k1rakishou.kpnc.domain.MessageReceiverImpl
import com.github.k1rakishou.kpnc.domain.ServerDeliveryNotifier
import com.github.k1rakishou.kpnc.domain.ServerDeliveryNotifierImpl
import com.github.k1rakishou.kpnc.domain.TokenUpdater
import com.github.k1rakishou.kpnc.domain.TokenUpdaterImpl
import com.github.k1rakishou.kpnc.model.data.Endpoints
import com.github.k1rakishou.kpnc.model.repository.AccountRepository
import com.github.k1rakishou.kpnc.model.repository.AccountRepositoryImpl
import com.github.k1rakishou.kpnc.model.repository.PostRepository
import com.github.k1rakishou.kpnc.model.repository.PostRepositoryImpl
import com.github.k1rakishou.kpnc.ui.main.MainViewModel
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

object KpncDependencyGraph {
  private val endpoints = Endpoints()

  fun initialize(applicationContext: Context): List<Module> {
    val modules = mutableListOf<Module>()
    modules += module {
      singletons(applicationContext)
      viewModels()
    }

    return modules
  }

  private fun Module.viewModels() {
    viewModel {
      MainViewModel(
        // TODO:
//        sharedPrefs = get(),
//        googleServicesChecker = get(),
//        messageReceiver = get(),
//        tokenUpdater = get(),
//        accountRepository = get()
      )
    }
  }

  private fun Module.singletons(applicationContext: Context) {
    single { applicationContext }
    single { applicationContext.getSharedPreferences("kpnc", Context.MODE_PRIVATE) }
    single { Moshi.Builder().build() }
    single { OkHttpClient.Builder().build() }
    single { endpoints }

    single<GoogleServicesChecker> { GoogleServicesCheckerImpl(applicationContext = get()) }
    single<AccountRepository> { AccountRepositoryImpl(endpoints = get(), okHttpClient = get(), moshi = get()) }
    single<PostRepository> {
      PostRepositoryImpl(
        endpoints = get(),
        okHttpClient = get(),
        moshi = get(),
        sharedPrefs = get()
      )
    }
    single<TokenUpdater> {
      TokenUpdaterImpl(
        sharedPrefs = get(),
        endpoints = endpoints,
        moshi = get(),
        okHttpClient = get()
      )
    }
    single<MessageReceiver> { MessageReceiverImpl() }

    single<KPNCFirebaseServiceDelegate> {
      KPNCFirebaseServiceDelegateImpl(
        sharedPrefs = get(),
        tokenUpdater = get(),
        messageProcessor = get()
      )
    }

    single<ServerDeliveryNotifier> {
      ServerDeliveryNotifierImpl(
        sharedPrefs = get(),
        endpoints = endpoints,
        moshi = get(),
        okHttpClient = get()
      )
    }
  }
}