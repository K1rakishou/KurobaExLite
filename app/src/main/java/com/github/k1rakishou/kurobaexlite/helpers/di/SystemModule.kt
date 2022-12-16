package com.github.k1rakishou.kurobaexlite.helpers.di

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.kurobaexlite.KurobaExLiteApplication
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module

internal fun Module.system(
  application: KurobaExLiteApplication,
  appCoroutineScope: CoroutineScope
) {
  single<Context> { application.applicationContext }
  single<Application> { application }
  single<NotificationManagerCompat> { NotificationManagerCompat.from(get()) }
  single<NotificationManager> { get<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
  single<KurobaExLiteApplication> { application }
  single<CoroutineScope> { appCoroutineScope }
}