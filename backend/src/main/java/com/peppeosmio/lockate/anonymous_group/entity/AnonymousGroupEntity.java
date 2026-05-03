package com.peppeosmio.lockate.anonymous_group.entity;

import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Entity
@Table(name = "anonymous_group")
@NoArgsConstructor
@Getter
@Setter
public class AnonymousGroupEntity {

    public AnonymousGroupEntity(
            EncryptedString encryptedName,
            byte[] memberPasswordSrpVerifier,
            byte[] memberPasswordSrpSalt,
            byte[] keySalt) {
        this.nameCipher = encryptedName.cipherText();
        this.nameIv = encryptedName.iv();
        this.memberPasswordSrpVerifier = memberPasswordSrpVerifier;
        this.memberPasswordSrpSalt = memberPasswordSrpSalt;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.keySalt = keySalt;
    }

    public static AnonymousGroupEntity fromBase64Fields(
            EncryptedDataDto encryptedNameDto,
            String memberPasswordSrpVerifier,
            String memberPasswordSrpSalt,
            String keySalt)
            throws Base64Exception {
        var decoder = Base64.getDecoder();
        try {
            return new AnonymousGroupEntity(
                    encryptedNameDto.toEncryptedString(),
                    decoder.decode(memberPasswordSrpVerifier),
                    decoder.decode(memberPasswordSrpSalt),
                    decoder.decode(keySalt));
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

    @Column(name = "member_password_srp_verifier", nullable = false)
    private byte[] memberPasswordSrpVerifier;

    @Column(name = "member_password_srp_salt", nullable = false)
    private byte[] memberPasswordSrpSalt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "key_salt", nullable = false)
    private byte[] keySalt;

    @OneToMany(mappedBy = "anonymousGroupEntity", fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC, id DESC")
    private List<AGMemberEntity> agMemberEntities;
}
