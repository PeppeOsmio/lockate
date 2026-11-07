package com.peppeosmio.lockate.common.dto;

import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;

public record EncryptedDataDto(@NotBlank String cipherText, @NotBlank String iv) {
  public static final int AES_GCM_IV_BYTES = 12;

  public EncryptedString toEncryptedString() {
    var decoder = Base64.getDecoder();
    return new EncryptedString(decoder.decode(cipherText), decoder.decode(iv));
  }

  /**
   * Every encrypted field has one size, so its length tells the server nothing and no member can
   * make every snapshot and every relay huge.
   */
  public EncryptedString toEncryptedStringOfLength(int cipherTextBytes)
      throws InvalidEncryptedDataException {
    var decoded = decodeWithValidIv();
    if (decoded.cipherText().length != cipherTextBytes) {
      throw new InvalidEncryptedDataException();
    }
    return decoded;
  }

  private EncryptedString decodeWithValidIv() throws InvalidEncryptedDataException {
    EncryptedString decoded;
    try {
      decoded = toEncryptedString();
    } catch (IllegalArgumentException e) {
      throw new InvalidEncryptedDataException();
    }
    if (decoded.iv().length != AES_GCM_IV_BYTES) {
      throw new InvalidEncryptedDataException();
    }
    return decoded;
  }

  public static EncryptedDataDto fromEncryptedString(EncryptedString encryptedString) {
    var encoder = Base64.getEncoder();
    return new EncryptedDataDto(
        encoder.encodeToString(encryptedString.cipherText()),
        encoder.encodeToString(encryptedString.iv()));
  }
}
