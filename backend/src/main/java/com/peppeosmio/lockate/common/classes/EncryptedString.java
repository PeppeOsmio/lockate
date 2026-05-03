package com.peppeosmio.lockate.common.classes;

public record EncryptedString(byte[] cipherText, byte[] iv) {

}
