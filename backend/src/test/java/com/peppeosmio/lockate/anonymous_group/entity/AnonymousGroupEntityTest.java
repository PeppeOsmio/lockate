package com.peppeosmio.lockate.anonymous_group.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AnonymousGroupEntityTest {

  private final Base64.Encoder encoder = Base64.getEncoder();

  @Test
  void fromBase64Fields_validBase64_createsEntity() throws Base64Exception {
    var dto =
        new EncryptedDataDto(
            encoder.encodeToString("cipher".getBytes()), encoder.encodeToString("iv".getBytes()));

    var entity =
        AnonymousGroupEntity.fromBase64Fields(
            dto,
            encoder.encodeToString("verifier".getBytes()),
            encoder.encodeToString("salt".getBytes()),
            encoder.encodeToString("keysalt".getBytes()));

    assertThat(entity.getNameCipher()).isEqualTo("cipher".getBytes());
    assertThat(entity.getNameIv()).isEqualTo("iv".getBytes());
    assertThat(entity.getMemberPasswordSrpVerifier()).isEqualTo("verifier".getBytes());
    assertThat(entity.getMemberPasswordSrpSalt()).isEqualTo("salt".getBytes());
    assertThat(entity.getKeySalt()).isEqualTo("keysalt".getBytes());
    assertThat(entity.getCreatedAt()).isNotNull();
  }

  @Test
  void fromBase64Fields_invalidBase64_throwsBase64Exception() {
    var dto =
        new EncryptedDataDto(
            encoder.encodeToString("cipher".getBytes()), encoder.encodeToString("iv".getBytes()));

    assertThatThrownBy(
            () ->
                AnonymousGroupEntity.fromBase64Fields(
                    dto, "not-valid-base64!!!", "not-valid-base64!!!", "not-valid-base64!!!"))
        .isInstanceOf(Base64Exception.class);
  }
}
