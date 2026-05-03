package com.peppeosmio.lockate.anonymous_group.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.dto.*;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.anonymous_group.exceptions.*;
import com.peppeosmio.lockate.anonymous_group.mapper.AGMemberMapper;
import com.peppeosmio.lockate.anonymous_group.mapper.AnonymousGroupMapper;
import com.peppeosmio.lockate.anonymous_group.repository.AGLocationRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AnonymousGroupRepository;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.anonymous_group.service.result.AGMemberAuthResult;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.exceptions.NotFoundException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.redis.RedisService;
import com.peppeosmio.lockate.srp.InvalidSrpSessionException;
import com.peppeosmio.lockate.srp.SrpService;

import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.crypto.CryptoException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
public class AnonymousGroupService {
    private final AGLocationConfigurationProperties agLocationConfigurationProperties;
    private final AnonymousGroupRepository anonymousGroupRepository;
    private final AGMemberRepository agMemberRepository;
    private final AGLocationRepository agLocationRepository;
    private final RedisService redisService;
    private final SrpService srpService;
    private final ObjectMapper objectMapper;
    private final AnonymousGroupMapper anonymousGroupMapper;
    private final AGMemberMapper agMemberMapper;

    public AnonymousGroupService(
            AGLocationConfigurationProperties agLocationConfigurationProperties,
            AnonymousGroupRepository anonymousGroupRepository,
            AGMemberRepository agMemberRepository,
            AGLocationRepository agLocationRepository,
            RedisService redisService,
            SrpService srpService,
            ObjectMapper objectMapper,
            AnonymousGroupMapper anonymousGroupMapper,
            AGMemberMapper agMemberMapper) {
        this.agLocationConfigurationProperties = agLocationConfigurationProperties;
        this.agMemberRepository = agMemberRepository;
        this.anonymousGroupRepository = anonymousGroupRepository;
        this.agLocationRepository = agLocationRepository;
        this.redisService = redisService;
        this.srpService = srpService;
        this.objectMapper = objectMapper;
        this.anonymousGroupMapper = anonymousGroupMapper;
        this.agMemberMapper = agMemberMapper;
    }

    private static String getRedisAGLocationChannel(UUID anonymousGroupId) {
        return "ag-" + anonymousGroupId.toString();
    }

    @Transactional
    private AGMemberWithTokenDto createMember(
            EncryptedDataDto encryptedUserNameDto,
            boolean isAGAdmin,
            AnonymousGroupEntity anonymousGroupEntity) {
        var secureRandom = new SecureRandom();
        var token = new byte[32];
        secureRandom.nextBytes(token);
        var agMemberEntity =
                agMemberRepository.save(
                        AGMemberEntity.fromBase64Fields(
                                encryptedUserNameDto, token, isAGAdmin, anonymousGroupEntity));
        var encoder = Base64.getEncoder();
        return new AGMemberWithTokenDto(
                agMemberMapper.toDto(agMemberEntity), encoder.encodeToString(token));
    }
    /**
     * Authenticates an Anonymous Group Member. If the provided anonymousGroupId is different from
     * the member's anonymous group's id this method will throw.
     *
     * @param anonymousGroupId
     * @param agMemberId
     * @param memberToken
     * @return
     * @throws UnauthorizedException
     * @throws AGNotFoundException
     */
    public AGMemberAuthentication authenticateMember(
            UUID anonymousGroupId, UUID agMemberId, String memberToken)
            throws UnauthorizedException, AGNotFoundException {
        anonymousGroupRepository
                .findById(anonymousGroupId)
                .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
        var agMemberEntity =
                agMemberRepository.findById(agMemberId).orElseThrow(UnauthorizedException::new);
        if (!agMemberEntity.getAnonymousGroupId().equals(anonymousGroupId)) {
            log.error("Wrong anonymousGroupId " + anonymousGroupId);
            throw new UnauthorizedException();
        }
        var decoder = Base64.getDecoder();
        if (!BCrypt.checkpw(decoder.decode(memberToken), agMemberEntity.getTokenHash())) {
            log.error("Wrong token " + memberToken);
            throw new UnauthorizedException();
        }
        return new AGMemberAuthentication(agMemberId);
    }

    @Transactional
    private List<AGMemberDto> listMembers(AnonymousGroupEntity agEntity) {
        var agMemberEntities = agMemberRepository.findMembersWithLastLocation(agEntity.getId());
        return agMemberEntities.stream().map(agMemberMapper::toDto).toList();
    }

    @Transactional
    public AGGetMembersResDto getMembers(
            UUID anonymousGroupId, AGMemberAuthentication authentication)
            throws AGNotFoundException {
        var agEntity =
                anonymousGroupRepository
                        .findById(anonymousGroupId)
                        .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
        var agMembers = listMembers(agEntity);
        return new AGGetMembersResDto(agMembers);
    }

    @Transactional
    public AGGetMembersCountDto getMembersCount(
            UUID anonymousGroupId, AGMemberAuthentication authentication)
            throws UnauthorizedException {

        return new AGGetMembersCountDto(
                agMemberRepository.countByAnonymousGroupId(anonymousGroupId));
    }

    @Transactional
    public AGCreateResDto createAnonymousGroup(AGCreateReqDto dto) throws Base64Exception {
        var agEntity =
                anonymousGroupRepository.save(
                        AnonymousGroupEntity.fromBase64Fields(
                                dto.encryptedGroupName(),
                                dto.memberPasswordSrpVerifier(),
                                dto.memberPasswordSrpSalt(),
                                dto.keySalt()));
        var agMemberWithTokenDto = createMember(dto.encryptedMemberName(), true, agEntity);
        return new AGCreateResDto(anonymousGroupMapper.toDto(agEntity), agMemberWithTokenDto);
    }

    public AGGetMemberPasswordSrpInfoResDto getMemberSrpInfo(UUID anonymousGroupId)
            throws AGNotFoundException {
        var anonymousGroupEntity =
                anonymousGroupRepository
                        .findById(anonymousGroupId)
                        .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
        var encoder = Base64.getEncoder();
        return new AGGetMemberPasswordSrpInfoResDto(
                EncryptedDataDto.fromEncryptedString(
                        new EncryptedString(
                                anonymousGroupEntity.getNameCipher(),
                                anonymousGroupEntity.getNameIv())),
                encoder.encodeToString(anonymousGroupEntity.getMemberPasswordSrpSalt()),
                encoder.encodeToString(anonymousGroupEntity.getKeySalt()));
    }

    @Transactional
    public AGMemberAuthStartResDto startMemberSrpAuth(
            UUID anonymousGroupId, AGMemberAuthStartReqDto dto)
            throws Base64Exception,
                    UnauthorizedException,
                    AGNotFoundException,
                    InvalidSrpSessionException {
        var anonymousGroupEntity =
                anonymousGroupRepository
                        .findById(anonymousGroupId)
                        .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
        try {
            var decoder = Base64.getDecoder();
            var srpSessionResult =
                    srpService.startSrp(
                            new BigInteger(decoder.decode(dto.A())),
                            new BigInteger(anonymousGroupEntity.getMemberPasswordSrpVerifier()));
            return new AGMemberAuthStartResDto(
                    srpSessionResult.sessionId(), srpSessionResult.srpSession().B());
        } catch (CryptoException e) {
            e.printStackTrace();
            throw new UnauthorizedException();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new Base64Exception();
        }
    }

    @Transactional
    public AGMemberAuthVerifyResDto verifyMemberSrpAuth(
            UUID anonymousGroupId, AGMemberAuthVerifyReqDto dto)
            throws UnauthorizedException,
                    NotFoundException,
                    InvalidSrpSessionException,
                    Base64Exception {
        var agEntity =
                anonymousGroupRepository
                        .findById(anonymousGroupId)
                        .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
        var decoder = Base64.getDecoder();
        var isValid = false;
        try {
            isValid =
                    srpService.verifySrp(
                            dto.srpSessionId(),
                            new BigInteger(agEntity.getMemberPasswordSrpVerifier()),
                            new BigInteger(decoder.decode(dto.M1())));

        } catch (CryptoException e) {
            e.printStackTrace();
            throw new UnauthorizedException();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new Base64Exception();
        }
        if (isValid) {
            var agMemberWithTokenDto = createMember(dto.encryptedMemberName(), false, agEntity);
            var agMembers = listMembers(agEntity);
            return new AGMemberAuthVerifyResDto(
                    anonymousGroupMapper.toDto(agEntity), agMemberWithTokenDto, agMembers);
        } else {
            throw new UnauthorizedException();
        }
    }

    @Transactional
    public void deleteMember(UUID anonymousGroupId, AGMemberAuthentication authentication)
            throws UnauthorizedException {
        if (!(authentication instanceof AGMemberAuthentication agMemberAuthentication)) {
            throw new UnauthorizedException();
        }
        var agMemberEntity =
                agMemberRepository
                        .findById(anonymousGroupId)
                        .orElseThrow(UnauthorizedException::new);
        if (!agMemberEntity.getAnonymousGroupId().equals(anonymousGroupId)) {
            throw new UnauthorizedException();
        }
        agMemberRepository.deleteById(agMemberAuthentication.getId());
    }

    /**
     * @param anonymousGroupId
     * @param authentication
     * @param dto
     * @param lastSavedLocationTimeStamp
     * @return the LocalDateTime if it was saved in the db
     * @throws AGNotFoundException
     * @throws UnauthorizedException
     * @throws JsonProcessingException
     */
    @Transactional
    public Optional<LocalDateTime> saveLocation(
            UUID anonymousGroupId,
            AGMemberAuthentication authentication,
            AGLocationSaveReqDto dto,
            @Nullable LocalDateTime lastSavedLocationTimeStamp)
            throws AGNotFoundException, UnauthorizedException, JsonProcessingException {
        if (!(authentication instanceof AGMemberAuthentication agMemberAuthentication)) {
            throw new UnauthorizedException();
        }
        // should never throw
        var anonymousGroupEntity =
                anonymousGroupRepository
                        .findById(anonymousGroupId)
                        .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
        // should never throw
        var agMemberEntity =
                agMemberRepository
                        .findById(agMemberAuthentication.getId())
                        .orElseThrow(UnauthorizedException::new);
        var timestamp = LocalDateTime.now(ZoneOffset.UTC);
        var locationUpdate =
                new LocationUpdateDto(
                        new LocationRecordDto(dto.encryptedLocation(), timestamp),
                        agMemberAuthentication.getId());
        var messageJson = objectMapper.writeValueAsString(locationUpdate);
        redisService.publish(getRedisAGLocationChannel(anonymousGroupId), messageJson);
        if (lastSavedLocationTimeStamp == null) {
            var lastSavedLocation =
                    agLocationRepository
                            .findFirstByAgMemberEntityOrderByTimestampDescIdDesc(agMemberEntity)
                            .orElse(null);
            if (lastSavedLocation != null) {
                lastSavedLocationTimeStamp = lastSavedLocation.getTimestamp();
            }
        }
        var shouldSave = false;
        if (lastSavedLocationTimeStamp == null) {
            shouldSave = true;
        } else {
            shouldSave =
                    Duration.between(lastSavedLocationTimeStamp, timestamp).toMillis()
                            >= agLocationConfigurationProperties.getSaveInterval().toMillis();
        }
        if (shouldSave) {
            var lastLocation =
                    agLocationRepository.save(
                            AGMemberLocationEntity.fromBase64Fields(
                                    dto.encryptedLocation(), agMemberEntity, timestamp));
            agMemberEntity.setLastLocation(lastLocation);
            agMemberRepository.save(agMemberEntity);
            return Optional.of(timestamp);
        }
        return Optional.empty();
    }

    public Runnable streamLocations(
            UUID anonymousGroupId,
            Consumer<LocationUpdateDto> onLocation,
            AGMemberAuthentication authentication) {
        log.info(
                "[SSE] Streaming locations: anonymousGroupId="
                        + anonymousGroupId
                        + " memberId="
                        + authentication.getId());
        var channel = getRedisAGLocationChannel(anonymousGroupId);
        var messageListener =
                redisService.subscribe(
                        channel,
                        (message) -> {
                            try {
                                var agLocationUpdate =
                                        objectMapper.readValue(message, LocationUpdateDto.class);
                                if (!agLocationUpdate
                                        .memberId()
                                        .equals(authentication.getId())) {
                                    onLocation.accept(agLocationUpdate);
                                }
                            } catch (JsonProcessingException e) {
                                e.printStackTrace();
                            }
                        });
        return () -> {
            redisService.unsubscribe(channel, messageListener);
            log.info(
                    "[SSE] Stopped streaming locations: anoymousGroupId="
                            + anonymousGroupId
                            + " memberId="
                            + authentication.getId());
        };
    }

    @Transactional
    public void deleteAnonymousGroup(UUID anonymousGroupId, AGMemberAuthentication authentication)
            throws UnauthorizedException, AGMemberNotAdminException {
        if (!(authentication instanceof AGMemberAuthentication agMemberAuthentication)) {
            throw new UnauthorizedException();
        }
        var agMemberEntity =
                agMemberRepository
                        .findById(agMemberAuthentication.getId())
                        .orElseThrow(UnauthorizedException::new);
        if (!agMemberEntity.isAGAdmin()) {
            throw new AGMemberNotAdminException();
        }
        anonymousGroupRepository.deleteAnonymousGroup(anonymousGroupId);
    }
}
