package com.peppeosmio.lockate.anonymous_group.entity;

import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
    name = "ag_member_location",
    indexes = {
      @Index(name = "idx_location_timestamp", columnList = "timestamp"),
      @Index(name = "idx_ag_location_ag_member_id", columnList = "ag_member_id")
    })
@Getter
@Setter
@NoArgsConstructor
public class AGMemberLocationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "payload_cipher", nullable = false)
  private byte[] payloadCipher;

  @Column(name = "payload_iv", nullable = false)
  private byte[] payloadIv;

  @Column(name = "timestamp", nullable = false)
  private LocalDateTime timestamp;

  // Anonymous group member
  @Column(name = "ag_member_id", nullable = false, insertable = false, updatable = false)
  private UUID agMemberId;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "ag_member_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private AGMemberEntity agMemberEntity;

  public AGMemberLocationEntity(
      EncryptedString encryptedPayload, AGMemberEntity agMemberEntity, LocalDateTime timestamp) {
    this.payloadCipher = encryptedPayload.cipherText();
    this.payloadIv = encryptedPayload.iv();
    this.agMemberEntity = agMemberEntity;
    this.agMemberId = agMemberEntity.getId();
    this.timestamp = timestamp;
  }

  public static AGMemberLocationEntity fromBase64Fields(
      EncryptedDataDto encryptedPayloadDto,
      AGMemberEntity agMemberEntity,
      LocalDateTime timestamp) {
    var encryptedPayload = encryptedPayloadDto.toEncryptedString();
    return new AGMemberLocationEntity(encryptedPayload, agMemberEntity, timestamp);
  }
}
