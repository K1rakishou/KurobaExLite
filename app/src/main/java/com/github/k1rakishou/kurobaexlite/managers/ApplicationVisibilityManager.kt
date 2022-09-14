package com.github.k1rakishou.kurobaexlite.managers

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList
import logcat.LogPriority
import logcat.logcat

class ApplicationVisibilityManager : DefaultActivityLifecycleCallbacks() {
  private val listeners = CopyOnWriteArrayList<ApplicationVisibilityListener>()

  private var currentApplicationVisibility: ApplicationVisibility = ApplicationVisibility.Background
  private val createdActivities = mutableMapOf<String, MutableSet<Int>>()

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
    val wasBackground = createdActivities.values.all { it.isEmpty() }
    val activityFullName = activity::class.java.name
    val activityHash = activity.hashCode()

    createdActivities.getOrPut(
      key = activityFullName,
      defaultValue = { mutableSetOf() }
    ).also { set -> set.add(activityHash) }

    logcat(TAG, LogPriority.VERBOSE) { "onActivityStarted('${activity::class.java.simpleName}', ${activityHash})" }

    if (!wasBackground || createdActivities.values.all { it.isEmpty() }) {
      return
    }

    logcat(TAG) { "^^^ App went foreground ^^^" }

    _switchedToForegroundAt = SystemClock.elapsedRealtime()
    currentApplicationVisibility = ApplicationVisibility.Foreground

    listeners.forEach { listener ->
      listener.onApplicationVisibilityChanged(currentApplicationVisibility)
    }
  }

  override fun onActivityStopped(activity: Activity) {
    val wasForeground = createdActivities.values.any { it.isNotEmpty() }
    val activityFullName = activity::class.java.name
    val activityHash = activity.hashCode()
    createdActivities[activityFullName]?.remove(activityHash)

    logcat(TAG, LogPriority.VERBOSE) { "onActivityStopped('${activity::class.java.simpleName}', ${activityHash})" }

    if (!wasForeground || createdActivities.values.any { it.isNotEmpty() }) {
      return
    }

    logcat(TAG) { "vvv App went background vvv" }

    currentApplicationVisibility = ApplicationVisibility.Background
    listeners.forEach { listener -> listener.onApplicationVisibilityChanged(currentApplicationVisibility) }
  }

  fun getCurrentAppVisibility(): ApplicationVisibility = currentApplicationVisibility
  fun isAppInForeground(): Boolean = getCurrentAppVisibility() == ApplicationVisibility.Foreground

  // "Maybe" because the app may get started for whatever reason (a service got invoked by the OS) but
  // no activities are going to start up.
  fun isAppStartingUpMaybe(): Boolean = _switchedToForegroundAt == null

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