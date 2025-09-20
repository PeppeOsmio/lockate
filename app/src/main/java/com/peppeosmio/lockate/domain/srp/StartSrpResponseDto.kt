package com.peppeosmio.lockate.domain.srp

data class StartSrpResponseDto(
    val sessionId: String,
    val B: String
)