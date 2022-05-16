package com.github.k1rakishou.kurobaexlite.helpers.html

import com.github.k1rakishou.kurobaexlite.model.ClientException
import java.io.InputStream

interface HtmlReader<R> {
  fun readHtml(url: String, inputStream: InputStream): R
}

class SearchException(message: String) : ClientException(message)