package com.github.k1rakishou.kurobaexlite.managers

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.CopyOnWriteArrayList
import logcat.logcat

class ApplicationVisibilityManager : DefaultActivityLifecycleCallbacks() {
  private val listeners = CopyOnWriteArrayList<ApplicationVisibilityListener>()

  private var currentApplicationVisibility: ApplicationVisibility = ApplicationVisibility.Background
  private var activityForegroundCounter = 0

  private var _switchedToForegroundAt: Long? = null
  val switchedToForegroundAt: Long?
    get() = _switchedToForegroundAt

  fun addListener(listener: ApplicationVisibilityListener) {
    listeners += listener
  }

  fun removeListener(listener: ApplicationVisibilityListener) {
    listeners -= listener
  }

  override fun onActivityStarted(activity: Activity) {
    val lastForeground = activityForegroundCounter
    activityForegroundCounter++

    if (activityForegroundCounter == lastForeground) {
      return
    }

    logcat(TAG) { "^^^ App went foreground ^^^" }

    _switchedToForegroundAt = System.currentTimeMillis()
    currentApplicationVisibility = ApplicationVisibility.Foreground
    listeners.forEach { listener -> listener.onApplicationVisibilityChanged(currentApplicationVisibility) }
  }

  override fun onActivityStopped(activity: Activity) {
    val lastForeground = activityForegroundCounter
    activityForegroundCounter--

    if (activityForegroundCounter < 0) {
      activityForegroundCounter = 0
    }

    if (activityForegroundCounter == lastForeground) {
      return
    }

    logcat(TAG) { "vvv App went background vvv" }

    currentApplicationVisibility = ApplicationVisibility.Background
    listeners.forEach { listener -> listener.onApplicationVisibilityChanged(currentApplicationVisibility) }
  }

  fun getCurrentAppVisibility(): ApplicationVisibility = currentApplicationVisibility
  fun isAppInForeground(): Boolean = getCurrentAppVisibility() == ApplicationVisibility.Foreground

  // Maybe because the app may get started for whatever reason (service got invoked by the OS) but
  // no activities are going to start up.
  fun isMaybeAppStartingUp(): Boolean = _switchedToForegroundAt == null

  companion object {
    private const val TAG = "ApplicationVisibilityManager"
  }
}

fun interface ApplicationVisibilityListener {
  fun onApplicationVisibilityChanged(applicationVisibility: ApplicationVisibility)
}

open class DefaultActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
  }

  override fun onActivityStarted(activity: Activity) {
  }

  override fun onActivityResumed(activity: Activity) {
  }

  override fun onActivityPaused(activity: Activity) {
  }

  override fun onActivityStopped(activity: Activity) {
  }

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
  }

  override fun onActivityDestroyed(activity: Activity) {
  }

}

sealed class ApplicationVisibility {

  fun isInForeground(): Boolean = this is Foreground
  fun isInBackground(): Boolean = this is Background

  object Foreground : ApplicationVisibility() {
    override fun toString(): String {
      return "Foreground"
    }
  }

  object Background : ApplicationVisibility() {
    override fun toString(): String {
      return "Background"
    }
  }
}