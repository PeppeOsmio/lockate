package com.peppeosmio.lockate.anonymous_group.entity;

import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "coordinates_cipher", nullable = false)
    private byte[] coordinatesCipher;

    @Column(name = "coordinates_iv", nullable = false)
    private byte[] coordinatesIv;

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
            EncryptedString encryptedCoordinates,
            AGMemberEntity agMemberEntity,
            LocalDateTime timestamp) {
        this.coordinatesCipher = encryptedCoordinates.cipherText();
        this.coordinatesIv = encryptedCoordinates.iv();
        this.agMemberEntity = agMemberEntity;
        this.agMemberId = agMemberEntity.getId();
        this.timestamp = timestamp;
    }

    public static AGMemberLocationEntity fromBase64Fields(
            EncryptedDataDto encryptedCoordinatesDto,
            AGMemberEntity agMemberEntity,
            LocalDateTime timestamp) {
        var encryptedCoordinates = encryptedCoordinatesDto.toEncryptedString();
        return new AGMemberLocationEntity(encryptedCoordinates, agMemberEntity, timestamp);
    }
}
