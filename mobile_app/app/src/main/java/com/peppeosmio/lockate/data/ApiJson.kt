package com.peppeosmio.lockate.data

import io.ktor.serialization.kotlinx.json.DefaultJson
import kotlinx.serialization.json.Json

/** Fields a newer backend adds don't break an older app. */
val apiJson = Json(DefaultJson) { ignoreUnknownKeys = true }
