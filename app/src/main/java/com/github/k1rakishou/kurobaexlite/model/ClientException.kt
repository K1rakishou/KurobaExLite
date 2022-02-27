package com.github.k1rakishou.kurobaexlite.model

abstract class ClientException : Exception {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
}

class BadStatusResponseException(status: Int) : ClientException("Bad response status: ${status}")

class EmptyBodyResponseException : ClientException("Response has no body")