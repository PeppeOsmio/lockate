package com.peppeosmio.lockate.utils

data class ResultWithError<T>(val value: T?, val errorInfo: ErrorInfo?)
