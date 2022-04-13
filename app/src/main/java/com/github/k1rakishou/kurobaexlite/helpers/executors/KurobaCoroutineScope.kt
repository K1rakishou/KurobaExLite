package com.github.k1rakishou.kurobaexlite.helpers.executors

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

class KurobaCoroutineScope(
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : CoroutineScope {
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = job + dispatcher

  fun cancelChildren() {
    job.cancelChildren()
  }

  fun cancel() {
    job.cancel()
  }

}