package com.peppeosmio.lockate.exceptions

class NoPermissionException(private val permission : String) : Exception(permission) {
}