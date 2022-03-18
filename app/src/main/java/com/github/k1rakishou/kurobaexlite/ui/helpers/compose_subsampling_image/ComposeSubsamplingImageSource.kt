package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import java.io.InputStream

sealed class ComposeSubsamplingImageSource{

  inline fun <T : Any> useInputStream(crossinline func: (InputStream) -> T): T {
    val inputStream = when (this) {
      is Stream -> inputStream
    }

    return inputStream.use(func)
  }

  class Stream(val inputStream: InputStream) : ComposeSubsamplingImageSource()
}