package com.peppeosmio.lockate.common.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.peppeosmio.lockate.common.classes.EncryptedString;
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

  @Test
  void fromEncryptedString_encodesFieldsAsBase64() {
    var encryptedString = new EncryptedString("cipher".getBytes(), "iv".getBytes());

    var dto = EncryptedDataDto.fromEncryptedString(encryptedString);

    assertThat(dto.cipherText()).isEqualTo(Base64.getEncoder().encodeToString("cipher".getBytes()));
    assertThat(dto.iv()).isEqualTo(Base64.getEncoder().encodeToString("iv".getBytes()));
  }
}
