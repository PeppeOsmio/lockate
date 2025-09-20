package com.peppeosmio.lockate.service.srp

import java.math.BigInteger

data class SrpSaltAndA(
    val A: BigInteger,
    val salt: BigInteger,
)