package com.peppeosmio.lockate.data.version

import kotlinx.serialization.Serializable

/** `GET /api/version`: the backend's version and the majors of the Lockate API it serves. */
@Serializable data class ServerVersionDto(val version: String, val apis: List<Int>)
