package com.peppeosmio.lockate.common.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EncryptedDataDtoTest {

  @Test
  void toEncryptedString_decodesBase64Fields() {
    var encoder = Base64.getEncoder();
    var dto =
        new EncryptedDataDto(
            encoder.encodeToString("cipher".getBytes()), encoder.encodeToString("iv".getBytes()));

    var encryptedString = dto.toEncryptedString();

    assertThat(encryptedString.cipherText()).isEqualTo("cipher".getBytes());
    assertThat(encryptedString.iv()).isEqualTo("iv".getBytes());
  }

  @Test
  void toEncryptedString_invalidBase64_throwsIllegalArgumentException() {
    var dto = new EncryptedDataDto("not-valid-base64!!!", "not-valid-base64!!!");

    assertThatThrownBy(dto::toEncryptedString).isInstanceOf(IllegalArgumentException.class);
  }

  private static EncryptedDataDto ofSizes(int cipherTextBytes, int ivBytes) {
    var encoder = Base64.getEncoder();
    return new EncryptedDataDto(
        encoder.encodeToString(new byte[cipherTextBytes]),
        encoder.encodeToString(new byte[ivBytes]));
  }

  @Test
  void toEncryptedStringOfLength_exactLength_decodes() throws Exception {
    var encryptedString = ofSizes(24, 12).toEncryptedStringOfLength(24);

    assertThat(encryptedString.cipherText()).hasSize(24);
    assertThat(encryptedString.iv()).hasSize(12);
  }

  @Test
  void toEncryptedStringOfLength_shorterCipherText_throws() {
    assertThatThrownBy(() -> ofSizes(23, 12).toEncryptedStringOfLength(24))
        .isInstanceOf(InvalidEncryptedDataException.class);
  }

  @Test
  void toEncryptedStringOfLength_longerCipherText_throws() {
    assertThatThrownBy(() -> ofSizes(25, 12).toEncryptedStringOfLength(24))
        .isInstanceOf(InvalidEncryptedDataException.class);
  }

  @Test
  void toEncryptedStringOfLength_ivOfTheWrongSize_throws() {
    assertThatThrownBy(() -> ofSizes(24, 16).toEncryptedStringOfLength(24))
        .isInstanceOf(InvalidEncryptedDataException.class);
  }

  @Test
  void toEncryptedStringOfLength_invalidBase64_throws() {
    var dto = new EncryptedDataDto("not-valid-base64!!!", "not-valid-base64!!!");

    assertThatThrownBy(() -> dto.toEncryptedStringOfLength(24))
        .isInstanceOf(InvalidEncryptedDataException.class);
  }

  @Test
  void fromEncryptedString_encodesFieldsAsBase64() {
    var encryptedString = new EncryptedString("cipher".getBytes(), "iv".getBytes());

    var dto = EncryptedDataDto.fromEncryptedString(encryptedString);

    assertThat(dto.cipherText()).isEqualTo(Base64.getEncoder().encodeToString("cipher".getBytes()));
    assertThat(dto.iv()).isEqualTo(Base64.getEncoder().encodeToString("iv".getBytes()));
  }
}
