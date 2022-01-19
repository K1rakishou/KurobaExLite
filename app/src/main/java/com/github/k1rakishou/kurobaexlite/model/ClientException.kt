package com.github.k1rakishou.kurobaexlite.model

abstract class ClientException(message: String) : Exception(message)

class BadStatusResponseException(status: Int) : ClientException("Bad response status: ${status}")

class EmptyBodyResponseException : ClientException("Response has no body")