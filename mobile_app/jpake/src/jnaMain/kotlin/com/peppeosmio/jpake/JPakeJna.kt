package com.peppeosmio.jpake

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.NativeLongByReference

// Compiled into both the android and jvm targets (see jpake/build.gradle.kts), which load the same
// libjpake from jpake-native

@Suppress("FunctionName")
private interface JPakeLibrary : Library {
    fun jpake_new(isServer: Int, password: ByteArray, passwordLen: NativeLong): Pointer?
    fun jpake_output(
        handle: Pointer, step: Int, out: ByteArray, capacity: NativeLong, outLen: NativeLongByReference
    ): Int

    fun jpake_input(handle: Pointer, step: Int, data: ByteArray, len: NativeLong): Int
    fun jpake_derive_key(handle: Pointer, out: ByteArray): Int
    fun jpake_free(handle: Pointer)
}

private val library: JPakeLibrary by lazy { Native.load("jpake", JPakeLibrary::class.java) }

// A round-one point is 65 bytes and a proof at most 32
private const val OUTPUT_CAPACITY = 256

private class JnaJPakeNative(isServer: Boolean, password: ByteArray) : JPakeNative {
    private var handle: Pointer? = library.jpake_new(
        if (isServer) 1 else 0, password, NativeLong(password.size.toLong())
    ) ?: throw IllegalStateException("Can't start J-PAKE")

    private fun handle() = handle ?: throw JPakeNativeException(-1)

    override fun output(step: Int): ByteArray {
        val out = ByteArray(OUTPUT_CAPACITY)
        val length = NativeLongByReference()
        val status = library.jpake_output(
            handle(), step, out, NativeLong(OUTPUT_CAPACITY.toLong()), length
        )
        if (status != 0) throw JPakeNativeException(status)
        return out.copyOf(length.value.toInt())
    }

    override fun input(step: Int, data: ByteArray) {
        val status = library.jpake_input(handle(), step, data, NativeLong(data.size.toLong()))
        if (status != 0) throw JPakeNativeException(status)
    }

    override fun deriveKey(): ByteArray {
        val key = ByteArray(JPAKE_KEY_BYTES)
        val status = library.jpake_derive_key(handle(), key)
        if (status != 0) throw JPakeNativeException(status)
        return key
    }

    override fun close() {
        handle?.let { library.jpake_free(it) }
        handle = null
    }
}

internal actual fun createJPakeNative(isServer: Boolean, password: ByteArray): JPakeNative =
    JnaJPakeNative(isServer, password)
