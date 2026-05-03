package com.peppeosmio.lockate.anonymous_group.entity;

import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;

import jakarta.persistence.*;

import lombok.*;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "ag_member",
        indexes = {@Index(name = "idx_ag_member_ag_id", columnList = "anonymous_group_id")})
@Getter
@Setter
@NoArgsConstructor
public class AGMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    // Encrypted fields
    @Column(name = "name_cipher", nullable = false)
    private byte[] nameCipher;

    @Column(name = "name_iv", nullable = false)
    private byte[] nameIv;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Anonymous group
    @Column(name = "anonymous_group_id", nullable = false, insertable = false, updatable = false)
    private UUID anonymousGroupId;

    @Column(name = "is_admin", nullable = false)
    private boolean isAGAdmin;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "anonymous_group_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private AnonymousGroupEntity anonymousGroupEntity;

    @OneToMany(mappedBy = "agMemberEntity", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<AGMemberLocationEntity> agMemberLocationEntities;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_location_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private AGMemberLocationEntity lastLocation;

    public AGMemberEntity(
            EncryptedString encryptedUserName,
            byte[] token,
            boolean isAGAdmin,
            AnonymousGroupEntity anonymousGroupEntity) {
        var tokenHash = BCrypt.hashpw(token, BCrypt.gensalt());
        this.nameCipher = encryptedUserName.cipherText();
        this.nameIv = encryptedUserName.iv();
        this.tokenHash = tokenHash;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.isAGAdmin = isAGAdmin;
        this.anonymousGroupEntity = anonymousGroupEntity;
        this.anonymousGroupId = anonymousGroupEntity.getId();
    }

    public static AGMemberEntity fromBase64Fields(
            EncryptedDataDto encryptedMemberNameDto,
            byte[] token,
            boolean isAGAdmin,
            AnonymousGroupEntity anonymousGroupEntity) {
        return new AGMemberEntity(
                encryptedMemberNameDto.toEncryptedString(), token, isAGAdmin, anonymousGroupEntity);
    }
}
