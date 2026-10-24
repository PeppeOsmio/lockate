package com.peppeosmio.lockate.anonymous_group.entity;

import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Entity
@Table(name = "anonymous_group")
@NoArgsConstructor
@Getter
@Setter
public class AnonymousGroupEntity {

  public AnonymousGroupEntity(EncryptedString encryptedName) {
    this.nameCipher = encryptedName.cipherText();
    this.nameIv = encryptedName.iv();
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public static AnonymousGroupEntity fromBase64Fields(EncryptedDataDto encryptedNameDto)
      throws Base64Exception {
    try {
      return new AnonymousGroupEntity(encryptedNameDto.toEncryptedString());
    } catch (IllegalArgumentException e) {
      throw new Base64Exception();
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name_cipher", nullable = false)
  private byte[] nameCipher;

  @Column(name = "name_iv", nullable = false)
  private byte[] nameIv;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "anonymousGroupEntity", fetch = FetchType.LAZY)
  @OrderBy("createdAt DESC, id DESC")
  private List<AGMemberEntity> agMemberEntities;
}
