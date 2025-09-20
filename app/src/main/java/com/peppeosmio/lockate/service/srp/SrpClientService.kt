package com.peppeosmio.lockate.service.srp

import dev.whyoleg.cryptography.bigint.BigInt
import dev.whyoleg.cryptography.bigint.toJavaBigInteger
import dev.whyoleg.cryptography.bigint.toKotlinBigInt
import org.bouncycastle.crypto.agreement.srp.SRP6Client
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.io.encoding.Base64


class SrpClientService() {

    fun generateVerifier(
        identifier: String, password: String, salt: ByteArray
    ): BigInteger {
        val params = SRP6StandardGroups.rfc5054_2048
        val gen = SRP6VerifierGenerator()
        gen.init(params, SHA256Digest())
        return gen.generateVerifier(
            salt, identifier.toByteArray(Charsets.UTF_8), password.toByteArray(Charsets.UTF_8)
        )
    }

    fun getSrpClient(): SRP6Client {
        // SRP
        // standard group params (2048-bit)
        val params = SRP6StandardGroups.rfc5054_2048

        // Initialize SRP server
        val random = SecureRandom()
        val srpClient = SRP6Client()
        srpClient.init(params, SHA256Digest(), random)
        return srpClient
    }

    fun getA(
        srpClient: SRP6Client, salt: String, identifier: String, password: String
    ): BigInt {
        val A = srpClient.generateClientCredentials(
            Base64.decode(salt),
            identifier.toByteArray(Charsets.UTF_8),
            password.toByteArray(Charsets.UTF_8),
        )
        return A.toKotlinBigInt()
    }

    fun getM1(srpClient: SRP6Client, B: BigInt): BigInt {
        srpClient.calculateSecret(B.toJavaBigInteger())
        val M1 = srpClient.calculateClientEvidenceMessage()
        return M1.toKotlinBigInt()
    }
}