package com.github.k1rakishou.kurobaexlite.base

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher

class GlobalConstants(
  private val appContext: Context
) {
  val coresCount by lazy { Runtime.getRuntime().availableProcessors() }

  @Suppress("MoveLambdaOutsideParentheses")
  val postParserDispatcher by lazy {
    val threadNameFormat = "post_parser_thread_%d"
    val mThreadId = AtomicInteger(0)

    return@lazy Executors.newFixedThreadPool(
      coresCount.coerceAtLeast(2),
      { runnable ->
        val thread = Thread(runnable)
        thread.name = String.format(threadNameFormat, mThreadId.getAndIncrement())

        return@newFixedThreadPool thread
      }
    ).asCoroutineDispatcher()
  }
}

