package com.peppeosmio.lockate.anonymous_group.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.common.classes.EncryptedString;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;

class AGMemberEntityTest {

  private final AnonymousGroupEntity anonymousGroupEntity =
      new AnonymousGroupEntity(
          new EncryptedString("groupname".getBytes(), "groupiv".getBytes()),
          "verifier".getBytes(),
          "salt".getBytes(),
          "keysalt".getBytes());

  @Test
  void constructor_hashesTokenAndSetsUtcCreatedAt() {
    var before = LocalDateTime.now(ZoneOffset.UTC);

    var entity =
        new AGMemberEntity(
            new EncryptedString("name".getBytes(), "iv".getBytes()),
            "raw-token".getBytes(),
            true,
            anonymousGroupEntity);

    var after = LocalDateTime.now(ZoneOffset.UTC);

    assertThat(entity.getTokenHash()).isNotEqualTo("raw-token");
    assertThat(BCrypt.checkpw("raw-token", entity.getTokenHash())).isTrue();
    assertThat(BCrypt.checkpw("wrong-token", entity.getTokenHash())).isFalse();
    assertThat(entity.getCreatedAt()).isBetween(before, after);
    assertThat(entity.isAGAdmin()).isTrue();
    assertThat(entity.getAnonymousGroupId()).isEqualTo(anonymousGroupEntity.getId());
  }
}
