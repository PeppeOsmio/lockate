package com.peppeosmio.lockate.exceptions

/** The address is already saved on this phone, as the server with [connectionId] */
class ServerAlreadyAddedException(val connectionId: Long) : Exception()
