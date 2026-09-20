package com.peppeosmio.lockate.anonymous_group.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AGMemberMapperTest {

  private final AGMemberMapper mapper = new AGMemberMapper(new LocationRecordMapper());

  private AnonymousGroupEntity anonymousGroupEntity;

  @BeforeEach
  void setUp() {
    anonymousGroupEntity =
        new AnonymousGroupEntity(
            new EncryptedString("groupname".getBytes(), "groupiv".getBytes()),
            "verifier".getBytes(),
            "salt".getBytes(),
            "keysalt".getBytes());
  }

  @Test
  void toDto_withoutLastLocation_mapsFieldsAndReturnsEmptyOptional() {
    var entity =
        new AGMemberEntity(
            new EncryptedString("name-cipher".getBytes(), "name-iv".getBytes()),
            "token".getBytes(),
            true,
            anonymousGroupEntity);

    var dto = mapper.toDto(entity);

    assertThat(dto.id()).isEqualTo(entity.getId());
    assertThat(dto.isAGAdmin()).isTrue();
    assertThat(dto.createdAt()).isEqualTo(entity.getCreatedAt());
    assertThat(dto.encryptedName().cipherText())
        .isEqualTo(Base64.getEncoder().encodeToString("name-cipher".getBytes()));
    assertThat(dto.encryptedName().iv())
        .isEqualTo(Base64.getEncoder().encodeToString("name-iv".getBytes()));
    assertThat(dto.encryptedLastLocationRecord()).isEmpty();
  }

  @Test
  void toDto_withLastLocation_returnsPresentOptional() {
    var entity =
        new AGMemberEntity(
            new EncryptedString("name-cipher".getBytes(), "name-iv".getBytes()),
            "token".getBytes(),
            false,
            anonymousGroupEntity);
    var locationEntity =
        new AGMemberLocationEntity(
            new EncryptedString("coord-cipher".getBytes(), "coord-iv".getBytes()),
            entity,
            LocalDateTime.now());
    entity.setLastLocation(locationEntity);

    var dto = mapper.toDto(entity);

    assertThat(dto.encryptedLastLocationRecord()).isPresent();
    assertThat(dto.encryptedLastLocationRecord().get().encryptedCoordinates().cipherText())
        .isEqualTo(Base64.getEncoder().encodeToString("coord-cipher".getBytes()));
  }
}
