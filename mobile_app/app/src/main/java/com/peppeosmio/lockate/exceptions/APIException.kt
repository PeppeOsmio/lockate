package com.peppeosmio.lockate.exceptions

class APIException(val statusCode: Int, val body: String) : Exception() {
}