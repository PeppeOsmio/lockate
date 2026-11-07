package com.peppeosmio.lockate.anonymous_group.service;

import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.dto.*;
import com.peppeosmio.lockate.anonymous_group.entity.AGCircleEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.anonymous_group.exceptions.*;
import com.peppeosmio.lockate.anonymous_group.mapper.AGMemberMapper;
import com.peppeosmio.lockate.anonymous_group.mapper.AnonymousGroupMapper;
import com.peppeosmio.lockate.anonymous_group.repository.AGCircleRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AGGroupSummaryProjection;
import com.peppeosmio.lockate.anonymous_group.repository.AGLocationRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AnonymousGroupRepository;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.dto.PageQueryDto;
import com.peppeosmio.lockate.common.dto.PageResponseDto;
import com.peppeosmio.lockate.common.exceptions.InvalidEncryptedDataException;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.invite.InviteService;
import com.peppeosmio.lockate.invite.exceptions.InvalidJoinTokenException;
import com.peppeosmio.lockate.redis.RedisService;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class AnonymousGroupService {
  private final AGLocationConfigurationProperties agLocationConfigurationProperties;
  private final AnonymousGroupRepository anonymousGroupRepository;
  private final AGMemberRepository agMemberRepository;
  private final AGLocationRepository agLocationRepository;
  private final AGCircleRepository agCircleRepository;
  private final RedisService redisService;
  private final InviteService inviteService;
  private final ObjectMapper objectMapper;
  private final AnonymousGroupMapper anonymousGroupMapper;
  private final AGMemberMapper agMemberMapper;

  public AnonymousGroupService(
      AGLocationConfigurationProperties agLocationConfigurationProperties,
      AnonymousGroupRepository anonymousGroupRepository,
      AGMemberRepository agMemberRepository,
      AGLocationRepository agLocationRepository,
      AGCircleRepository agCircleRepository,
      RedisService redisService,
      InviteService inviteService,
      ObjectMapper objectMapper,
      AnonymousGroupMapper anonymousGroupMapper,
      AGMemberMapper agMemberMapper) {
    this.agLocationConfigurationProperties = agLocationConfigurationProperties;
    this.agMemberRepository = agMemberRepository;
    this.anonymousGroupRepository = anonymousGroupRepository;
    this.agLocationRepository = agLocationRepository;
    this.agCircleRepository = agCircleRepository;
    this.redisService = redisService;
    this.inviteService = inviteService;
    this.objectMapper = objectMapper;
    this.anonymousGroupMapper = anonymousGroupMapper;
    this.agMemberMapper = agMemberMapper;
  }

  public static final int MAX_CIRCLES_PER_GROUP = 50;
  // Every encrypted field has one size, plus the 16-byte GCM tag: two big-endian Int32
  // microdegrees, one activity byte, a Float64 radius, a name (of a group, member or circle) padded
  // to 128, a circle event (16-byte circle id and one enter/exit byte)
  public static final int COORDINATES_CIPHER_BYTES = 24;
  public static final int MEMBER_ACTIVITY_CIPHER_BYTES = 17;
  public static final int CIRCLE_RADIUS_CIPHER_BYTES = 24;
  public static final int NAME_CIPHER_BYTES = 144;
  public static final int CIRCLE_EVENT_CIPHER_BYTES = 33;

  public static String getRedisAGEventsChannel(UUID anonymousGroupId) {
    return "ag-" + anonymousGroupId + "-events";
  }

  public static String getRedisAGLocationsChannel(UUID anonymousGroupId) {
    return "ag-" + anonymousGroupId + "-locations";
  }

  private void publishFrame(UUID anonymousGroupId, AGFrameDto frame) {
    publish(getRedisAGEventsChannel(anonymousGroupId), frame);
  }

  /**
   * Goes out only once the transaction commits, so nobody hears of a change that is rolled back.
   */
  private void publish(String channel, Object message) {
    var json = objectMapper.writeValueAsString(message);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              redisService.publish(channel, json);
            }
          });
    } else {
      redisService.publish(channel, json);
    }
  }

  private CircleDto toCircleDto(AGCircleEntity entity) {
    return new CircleDto(
        entity.getId(),
        EncryptedDataDto.fromEncryptedString(entity.getEncryptedCoordinates()),
        EncryptedDataDto.fromEncryptedString(entity.getEncryptedRadius()),
        EncryptedDataDto.fromEncryptedString(entity.getEncryptedName()),
        entity.getUpdatedAt());
  }

  @Transactional
  public AGFrameDto getSnapshot(UUID anonymousGroupId) throws AGNotFoundException {
    var agEntity =
        anonymousGroupRepository
            .findById(anonymousGroupId)
            .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
    var circles =
        agCircleRepository.findByAnonymousGroupIdOrderByCreatedAtAscIdAsc(anonymousGroupId).stream()
            .map(this::toCircleDto)
            .toList();
    var members =
        listMembers(agEntity).stream()
            .map(
                m ->
                    new AGMemberDto(
                        m.id(), m.encryptedName(), m.createdAt(), m.isAGAdmin(), Optional.empty()))
            .toList();
    return AGFrameDto.snapshot(members, circles);
  }

  @Transactional
  public List<MemberLocationDto> getLocationSnapshot(UUID anonymousGroupId)
      throws AGNotFoundException {
    var agEntity =
        anonymousGroupRepository
            .findById(anonymousGroupId)
            .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
    return listMembers(agEntity).stream()
        .flatMap(
            m ->
                m.encryptedLastLocationRecord().stream()
                    .map(record -> new MemberLocationDto(m.id(), record)))
        .toList();
  }

  @Transactional
  public void upsertCircle(UUID anonymousGroupId, UUID circleId, CircleUpsertReqDto dto)
      throws AGNotFoundException, AGCircleConflictException, InvalidEncryptedDataException {
    var coordinates =
        dto.encryptedCoordinates().toEncryptedStringOfLength(COORDINATES_CIPHER_BYTES);
    var radius = dto.encryptedRadius().toEncryptedStringOfLength(CIRCLE_RADIUS_CIPHER_BYTES);
    var name = dto.encryptedName().toEncryptedStringOfLength(NAME_CIPHER_BYTES);
    // locked, or two creations at once could both see room for one more circle
    var agEntity =
        anonymousGroupRepository
            .findByIdForUpdate(anonymousGroupId)
            .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
    var now = LocalDateTime.now(ZoneOffset.UTC);
    var existing = agCircleRepository.findById(circleId).orElse(null);
    AGCircleEntity circle;
    if (existing == null) {
      if (agCircleRepository.countByAnonymousGroupId(anonymousGroupId) >= MAX_CIRCLES_PER_GROUP) {
        throw new AGCircleConflictException("circle_limit_reached");
      }
      circle =
          agCircleRepository.save(
              new AGCircleEntity(circleId, coordinates, radius, name, agEntity, now));
    } else if (!existing.getAnonymousGroupId().equals(anonymousGroupId)) {
      throw new AGCircleConflictException("circle_id_taken");
    } else {
      existing.setEncryptedFields(coordinates, radius, name, now);
      circle = existing;
    }
    publishFrame(anonymousGroupId, AGFrameDto.circleUpserted(toCircleDto(circle)));
  }

  @Transactional
  public void deleteCircle(UUID anonymousGroupId, UUID circleId) throws AGCircleNotFoundException {
    var circle =
        agCircleRepository
            .findByIdAndAnonymousGroupId(circleId, anonymousGroupId)
            .orElseThrow(() -> new AGCircleNotFoundException(circleId));
    agCircleRepository.delete(circle);
    publishFrame(anonymousGroupId, AGFrameDto.circleDeleted(circleId));
  }

  private void requireFreeMemberId(UUID memberId) throws AGIdTakenException {
    if (agMemberRepository.existsById(memberId)) {
      throw new AGIdTakenException("ag_member_id_taken");
    }
  }

  @Transactional
  private AGMemberWithTokenDto createMember(
      UUID memberId,
      EncryptedString encryptedName,
      boolean isAGAdmin,
      AnonymousGroupEntity anonymousGroupEntity) {
    var secureRandom = new SecureRandom();
    var token = new byte[32];
    secureRandom.nextBytes(token);
    var agMemberEntity =
        agMemberRepository.save(
            new AGMemberEntity(memberId, encryptedName, token, isAGAdmin, anonymousGroupEntity));
    var encoder = Base64.getEncoder();
    return new AGMemberWithTokenDto(
        agMemberMapper.toDto(agMemberEntity), encoder.encodeToString(token));
  }

  /**
   * Authenticates an Anonymous Group Member. If the provided anonymousGroupId is different from the
   * member's anonymous group's id this method will throw.
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
      log.error("Wrong token for member " + agMemberId);
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
  public AGGetMembersResDto getMembers(UUID anonymousGroupId, AGMemberAuthentication authentication)
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
      UUID anonymousGroupId, AGMemberAuthentication authentication) throws UnauthorizedException {

    return new AGGetMembersCountDto(agMemberRepository.countByAnonymousGroupId(anonymousGroupId));
  }

  @Transactional
  public AGCreateResDto createAnonymousGroup(AGCreateReqDto dto)
      throws InvalidEncryptedDataException, AGIdTakenException {
    var groupName = dto.encryptedGroupName().toEncryptedStringOfLength(NAME_CIPHER_BYTES);
    var memberName = dto.encryptedMemberName().toEncryptedStringOfLength(NAME_CIPHER_BYTES);
    if (anonymousGroupRepository.existsById(dto.groupId())) {
      throw new AGIdTakenException("ag_id_taken");
    }
    requireFreeMemberId(dto.memberId());
    var agEntity =
        anonymousGroupRepository.save(new AnonymousGroupEntity(dto.groupId(), groupName));
    var agMemberWithTokenDto = createMember(dto.memberId(), memberName, true, agEntity);
    return new AGCreateResDto(anonymousGroupMapper.toDto(agEntity), agMemberWithTokenDto);
  }

  /**
   * Adds a member with the join token an existing member sent inside the invite payload. Everything
   * else is checked first so a typo in the group id or a bad request doesn't burn the token.
   */
  @Transactional
  public AGJoinResDto joinWithInvite(UUID anonymousGroupId, AGJoinReqDto dto)
      throws AGNotFoundException,
          InvalidJoinTokenException,
          InvalidEncryptedDataException,
          AGIdTakenException {
    var memberName = dto.encryptedMemberName().toEncryptedStringOfLength(NAME_CIPHER_BYTES);
    var agEntity =
        anonymousGroupRepository
            .findById(anonymousGroupId)
            .orElseThrow(() -> new AGNotFoundException(anonymousGroupId));
    requireFreeMemberId(dto.memberId());
    inviteService.consumeJoinToken(anonymousGroupId, dto.joinToken());
    var agMemberWithTokenDto = createMember(dto.memberId(), memberName, false, agEntity);
    var agMembers = listMembers(agEntity);
    publishFrame(anonymousGroupId, AGFrameDto.memberJoined(agMemberWithTokenDto.member()));
    return new AGJoinResDto(anonymousGroupMapper.toDto(agEntity), agMemberWithTokenDto, agMembers);
  }

  @Transactional
  public void deleteMember(UUID anonymousGroupId, AGMemberAuthentication authentication)
      throws UnauthorizedException {
    if (!(authentication instanceof AGMemberAuthentication agMemberAuthentication)) {
      throw new UnauthorizedException();
    }
    var agMemberEntity =
        agMemberRepository
            .findById(agMemberAuthentication.getId())
            .orElseThrow(UnauthorizedException::new);
    if (!agMemberEntity.getAnonymousGroupId().equals(anonymousGroupId)) {
      throw new UnauthorizedException();
    }
    agMemberRepository.deleteById(agMemberAuthentication.getId());
    publishFrame(anonymousGroupId, AGFrameDto.memberLeft(agMemberAuthentication.getId()));
  }

  /**
   * @param anonymousGroupId
   * @param authentication
   * @param dto
   * @param lastSavedLocationTimeStamp
   * @return the LocalDateTime if it was saved in the db
   * @throws AGNotFoundException
   * @throws UnauthorizedException
   */
  @Transactional
  public Optional<LocalDateTime> relayMemberMessage(
      UUID anonymousGroupId,
      AGMemberAuthentication authentication,
      AGMemberMessageReqDto dto,
      @Nullable LocalDateTime lastSavedLocationTimeStamp)
      throws AGNotFoundException, UnauthorizedException, InvalidEncryptedDataException {
    if (!(authentication instanceof AGMemberAuthentication agMemberAuthentication)) {
      throw new UnauthorizedException();
    }
    if (dto.encryptedCoordinates() == null || dto.encryptedActivity() == null) {
      throw new InvalidEncryptedDataException();
    }
    var encryptedCoordinates =
        dto.encryptedCoordinates().toEncryptedStringOfLength(COORDINATES_CIPHER_BYTES);
    var encryptedActivity =
        dto.encryptedActivity().toEncryptedStringOfLength(MEMBER_ACTIVITY_CIPHER_BYTES);
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
    publish(
        getRedisAGLocationsChannel(anonymousGroupId),
        new MemberLocationDto(
            agMemberAuthentication.getId(),
            new LocationRecordDto(dto.encryptedCoordinates(), dto.encryptedActivity(), timestamp)));
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
              new AGMemberLocationEntity(
                  encryptedCoordinates, encryptedActivity, agMemberEntity, timestamp));
      agMemberEntity.setLastLocation(lastLocation);
      agMemberRepository.save(agMemberEntity);
      return Optional.of(timestamp);
    }
    return Optional.empty();
  }

  /** Relays a circle event to the other members, without storing it. */
  public void relayCircleEvent(
      UUID anonymousGroupId, AGMemberAuthentication authentication, AGCircleEventReqDto dto)
      throws InvalidEncryptedDataException {
    if (dto.encryptedEvent() == null) {
      throw new InvalidEncryptedDataException();
    }
    dto.encryptedEvent().toEncryptedStringOfLength(CIRCLE_EVENT_CIPHER_BYTES);
    publishFrame(
        anonymousGroupId,
        AGFrameDto.circleEventOf(
            authentication.getId(),
            new CircleEventDto(dto.encryptedEvent(), LocalDateTime.now(ZoneOffset.UTC))));
  }

  @Transactional
  public PageResponseDto<AGAdminSummaryDto> listGroupsForAdmin(PageQueryDto query) {
    var pageable = PageRequest.of(query.page(), query.size(), Sort.by("createdAt").descending());
    var page = anonymousGroupRepository.findAll(pageable);
    var groups = page.getContent();
    var groupIds = groups.stream().map(AnonymousGroupEntity::getId).toList();
    var summaries =
        groupIds.isEmpty()
            ? Map.<UUID, AGGroupSummaryProjection>of()
            : agMemberRepository.summarizeByGroupIds(groupIds).stream()
                .collect(
                    Collectors.toMap(AGGroupSummaryProjection::getGroupId, summary -> summary));
    var items =
        groups.stream()
            .map(
                group -> {
                  var summary = summaries.get(group.getId());
                  return new AGAdminSummaryDto(
                      group.getId(),
                      group.getCreatedAt(),
                      summary == null ? 0 : summary.getMemberCount(),
                      summary == null ? null : summary.getLastLocationAt());
                })
            .toList();
    return PageResponseDto.of(page, items);
  }

  @Transactional
  public List<AGAdminMemberDto> listMembersForAdmin(UUID anonymousGroupId)
      throws AGNotFoundException {
    if (!anonymousGroupRepository.existsById(anonymousGroupId)) {
      throw new AGNotFoundException(anonymousGroupId);
    }
    return agMemberRepository.findMembersWithLastLocation(anonymousGroupId).stream()
        .map(
            member ->
                new AGAdminMemberDto(
                    member.getId(),
                    member.getCreatedAt(),
                    member.isAGAdmin(),
                    member.getLastLocation() == null
                        ? null
                        : member.getLastLocation().getTimestamp()))
        .toList();
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
    publishFrame(anonymousGroupId, AGFrameDto.groupDeleted());
  }

  @Transactional
  public void promoteMember(
      UUID anonymousGroupId, AGMemberAuthentication authentication, UUID agMemberId)
      throws UnauthorizedException, AGMemberNotAdminException, AGMemberNotFoundException {
    var caller =
        agMemberRepository.findById(authentication.getId()).orElseThrow(UnauthorizedException::new);
    if (!caller.isAGAdmin()) {
      throw new AGMemberNotAdminException();
    }
    var target =
        agMemberRepository
            .findByIdAndAnonymousGroupId(agMemberId, anonymousGroupId)
            .orElseThrow(() -> new AGMemberNotFoundException(agMemberId));
    target.setAGAdmin(true);
    publishFrame(anonymousGroupId, AGFrameDto.memberUpdated(agMemberId, true));
  }

  @Transactional
  public void deleteAnonymousGroupForAdmin(UUID anonymousGroupId) throws AGNotFoundException {
    if (!anonymousGroupRepository.existsById(anonymousGroupId)) {
      throw new AGNotFoundException(anonymousGroupId);
    }
    anonymousGroupRepository.deleteAnonymousGroup(anonymousGroupId);
    publishFrame(anonymousGroupId, AGFrameDto.groupDeleted());
  }

  @Transactional
  public void deleteMemberForAdmin(UUID anonymousGroupId, UUID agMemberId)
      throws AGMemberNotFoundException {
    var agMemberEntity =
        agMemberRepository
            .findByIdAndAnonymousGroupId(agMemberId, anonymousGroupId)
            .orElseThrow(() -> new AGMemberNotFoundException(agMemberId));
    agMemberRepository.deleteById(agMemberEntity.getId());
    publishFrame(anonymousGroupId, AGFrameDto.memberLeft(agMemberId));
  }

  @Transactional
  public void setMemberAdminForAdmin(UUID anonymousGroupId, UUID agMemberId, boolean isAGAdmin)
      throws AGMemberNotFoundException {
    var agMemberEntity =
        agMemberRepository
            .findByIdAndAnonymousGroupId(agMemberId, anonymousGroupId)
            .orElseThrow(() -> new AGMemberNotFoundException(agMemberId));
    agMemberEntity.setAGAdmin(isAGAdmin);
    publishFrame(anonymousGroupId, AGFrameDto.memberUpdated(agMemberId, isAGAdmin));
  }
}
