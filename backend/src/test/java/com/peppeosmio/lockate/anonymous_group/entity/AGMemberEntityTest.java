package com.peppeosmio.lockate.anonymous_group.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.common.classes.EncryptedString;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AGMemberEntityTest {

  private final AnonymousGroupEntity anonymousGroupEntity =
      new AnonymousGroupEntity(
          UUID.randomUUID(), new EncryptedString("groupname".getBytes(), "groupiv".getBytes()));

  @Test
  void constructor_hashesTokenAndSetsUtcCreatedAt() throws Exception {
    var before = LocalDateTime.now(ZoneOffset.UTC);

    var entity =
        new AGMemberEntity(
            UUID.randomUUID(),
            new EncryptedString("name".getBytes(), "iv".getBytes()),
            "raw-token".getBytes(),
            true,
            anonymousGroupEntity);

    var after = LocalDateTime.now(ZoneOffset.UTC);

    assertThat(entity.getTokenHash())
        .isEqualTo(MessageDigest.getInstance("SHA-256").digest("raw-token".getBytes()));
    assertThat(entity.getCreatedAt()).isBetween(before, after);
    assertThat(entity.isAGAdmin()).isTrue();
    assertThat(entity.getAnonymousGroupId()).isEqualTo(anonymousGroupEntity.getId());
  }

  @Test
  void hasToken_matchesOnlyTheTokenItWasCreatedWith() {
    var entity =
        new AGMemberEntity(
            UUID.randomUUID(),
            new EncryptedString("name".getBytes(), "iv".getBytes()),
            "raw-token".getBytes(),
            false,
            anonymousGroupEntity);

    assertThat(entity.hasToken("raw-token".getBytes())).isTrue();
    assertThat(entity.hasToken("wrong-token".getBytes())).isFalse();
  }
}
