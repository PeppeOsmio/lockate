package com.peppeosmio.lockate.common.dto;

import com.peppeosmio.lockate.common.classes.EncryptedString;
import jakarta.validation.constraints.NotBlank;

import java.util.Base64;

public record EncryptedDataDto(@NotBlank String cipherText, @NotBlank String iv) {
    public EncryptedString toEncryptedString() {
        var decoder = Base64.getDecoder();
        return new EncryptedString(decoder.decode(cipherText), decoder.decode(iv));
    }

    public static EncryptedDataDto fromEncryptedString(EncryptedString encryptedString) {
        var encoder = Base64.getEncoder();
        return new EncryptedDataDto(
                encoder.encodeToString(encryptedString.cipherText()),
                encoder.encodeToString(encryptedString.iv()));
    }
}
