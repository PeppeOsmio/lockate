package com.peppeosmio.lockate.domain

data class Connection(
    val id: Long?,
    val name: String,
    val url: String,
    val apiKey: String?,
    val username: String?,
    val authToken: String?,
)
