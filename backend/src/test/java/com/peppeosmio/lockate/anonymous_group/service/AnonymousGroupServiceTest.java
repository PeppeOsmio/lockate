package com.peppeosmio.lockate.anonymous_group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.dto.*;
import com.peppeosmio.lockate.anonymous_group.entity.AGCircleEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGCircleConflictException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGCircleNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotAdminException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.mapper.AGMemberMapper;
import com.peppeosmio.lockate.anonymous_group.mapper.AnonymousGroupMapper;
import com.peppeosmio.lockate.anonymous_group.repository.AGCircleRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AGLocationRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AnonymousGroupRepository;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.dto.PageQueryDto;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.invite.InviteService;
import com.peppeosmio.lockate.invite.exceptions.InvalidJoinTokenException;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCrypt;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AnonymousGroupServiceTest {

  @Mock private AGLocationConfigurationProperties agLocationConfigurationProperties;
  @Mock private AnonymousGroupRepository anonymousGroupRepository;
  @Mock private AGMemberRepository agMemberRepository;
  @Mock private AGLocationRepository agLocationRepository;
  @Mock private AGCircleRepository agCircleRepository;
  @Mock private RedisService redisService;
  @Mock private InviteService inviteService;
  @Mock private ObjectMapper objectMapper;
  @Mock private AnonymousGroupMapper anonymousGroupMapper;
  @Mock private AGMemberMapper agMemberMapper;

  private AnonymousGroupService service;

  private final UUID groupId = UUID.randomUUID();
  private AnonymousGroupEntity groupEntity;

  @BeforeEach
  void setUp() {
    service =
        new AnonymousGroupService(
            agLocationConfigurationProperties,
            anonymousGroupRepository,
            agMemberRepository,
            agLocationRepository,
            agCircleRepository,
            redisService,
            inviteService,
            objectMapper,
            anonymousGroupMapper,
            agMemberMapper);

    groupEntity =
        new AnonymousGroupEntity(
            new EncryptedString("group-name".getBytes(), "group-iv".getBytes()));
    groupEntity.setId(groupId);
  }

  private AGMemberEntity newMember(
      UUID id, byte[] rawToken, boolean isAdmin, AnonymousGroupEntity group) {
    var member =
        new AGMemberEntity(
            new EncryptedString("member-name".getBytes(), "member-iv".getBytes()),
            rawToken,
            isAdmin,
            group);
    member.setId(id);
    return member;
  }

  // ---------- authenticateMember ----------

  @Test
  void authenticateMember_correctToken_returnsAuthentication() throws Exception {
    var memberId = UUID.randomUUID();
    var rawToken = "correct-token".getBytes();
    var member = newMember(memberId, rawToken, false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));

    var result =
        service.authenticateMember(groupId, memberId, Base64.getEncoder().encodeToString(rawToken));

    assertThat(result.getId()).isEqualTo(memberId);
  }

  @Test
  void authenticateMember_wrongToken_throwsUnauthorized() {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "correct-token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));

    assertThatThrownBy(
            () ->
                service.authenticateMember(
                    groupId,
                    memberId,
                    Base64.getEncoder().encodeToString("wrong-token".getBytes())))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void authenticateMember_wrongToken_neverLogsTheToken() {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "correct-token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    var sentToken = Base64.getEncoder().encodeToString("wrong-token".getBytes());
    var logger = (Logger) LoggerFactory.getLogger(AnonymousGroupService.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      assertThatThrownBy(() -> service.authenticateMember(groupId, memberId, sentToken))
          .isInstanceOf(UnauthorizedException.class);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).isNotEmpty();
    assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains(sentToken));
  }

  @Test
  void authenticateMember_memberBelongsToDifferentGroup_throwsUnauthorized() {
    var otherGroup =
        new AnonymousGroupEntity(new EncryptedString("other".getBytes(), "other-iv".getBytes()));
    otherGroup.setId(UUID.randomUUID());
    var memberId = UUID.randomUUID();
    var rawToken = "correct-token".getBytes();
    var member = newMember(memberId, rawToken, false, otherGroup);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));

    assertThatThrownBy(
            () ->
                service.authenticateMember(
                    groupId, memberId, Base64.getEncoder().encodeToString(rawToken)))
        .isInstanceOf(UnauthorizedException.class);
  }

  // ---------- createAnonymousGroup ----------

  @Test
  void createAnonymousGroup_returnsRawTokenButStoresHashedToken() throws Exception {
    when(anonymousGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(agMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var encoder = Base64.getEncoder();
    var dto =
        new AGCreateReqDto(
            new EncryptedDataDto(
                encoder.encodeToString("member".getBytes()),
                encoder.encodeToString("miv".getBytes())),
            new EncryptedDataDto(
                encoder.encodeToString("group".getBytes()),
                encoder.encodeToString("giv".getBytes())));

    var result = service.createAnonymousGroup(dto);

    var memberCaptor = ArgumentCaptor.forClass(AGMemberEntity.class);
    verify(agMemberRepository).save(memberCaptor.capture());
    var savedMember = memberCaptor.getValue();
    assertThat(savedMember.isAGAdmin()).isTrue();
    var rawToken = Base64.getDecoder().decode(result.authenticatedMemberInfo().token());
    assertThat(rawToken).hasSize(32);
    assertThat(savedMember.getTokenHash()).isNotEqualTo(result.authenticatedMemberInfo().token());
    assertThat(BCrypt.checkpw(rawToken, savedMember.getTokenHash())).isTrue();
  }

  // ---------- joinWithInvite ----------

  private AGJoinReqDto joinReq(String joinToken) {
    var encoder = Base64.getEncoder();
    return new AGJoinReqDto(
        joinToken,
        new EncryptedDataDto(
            encoder.encodeToString("member".getBytes()), encoder.encodeToString("miv".getBytes())));
  }

  @Test
  void joinWithInvite_validToken_createsNonAdminMemberAndReturnsMembers() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(agMemberRepository.findMembersWithLastLocation(groupId)).thenReturn(List.of());

    var result = service.joinWithInvite(groupId, joinReq("token"));

    verify(inviteService).consumeJoinToken(groupId, "token");
    var memberCaptor = ArgumentCaptor.forClass(AGMemberEntity.class);
    verify(agMemberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().isAGAdmin()).isFalse();
    assertThat(Base64.getDecoder().decode(result.authenticatedMemberInfo().token())).hasSize(32);
    assertThat(result.members()).isEmpty();
  }

  @Test
  void joinWithInvite_invalidToken_createsNoMember() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    doThrow(new InvalidJoinTokenException()).when(inviteService).consumeJoinToken(groupId, "bad");

    assertThatThrownBy(() -> service.joinWithInvite(groupId, joinReq("bad")))
        .isInstanceOf(InvalidJoinTokenException.class);
    verify(agMemberRepository, never()).save(any());
  }

  @Test
  void joinWithInvite_missingGroup_throwsAGNotFoundWithoutConsumingTheToken() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.joinWithInvite(groupId, joinReq("token")))
        .isInstanceOf(AGNotFoundException.class);
    verify(inviteService, never()).consumeJoinToken(any(), any());
  }

  // ---------- deleteMember ----------

  @Test
  void deleteMember_legitimateMember_deletesThemselves() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    var authentication = new AGMemberAuthentication(memberId);

    service.deleteMember(groupId, authentication);

    verify(agMemberRepository).deleteById(memberId);
  }

  @Test
  void deleteMember_memberFromDifferentGroup_throwsUnauthorized() {
    var otherGroup =
        new AnonymousGroupEntity(new EncryptedString("other".getBytes(), "other-iv".getBytes()));
    otherGroup.setId(UUID.randomUUID());
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, otherGroup);
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    var authentication = new AGMemberAuthentication(memberId);

    assertThatThrownBy(() -> service.deleteMember(groupId, authentication))
        .isInstanceOf(UnauthorizedException.class);
    verify(agMemberRepository, never()).deleteById(any());
  }

  // ---------- getMembers ----------

  @Test
  void getMembers_existingGroup_returnsMappedMembers() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findMembersWithLastLocation(groupId)).thenReturn(List.of(member));
    var expectedDto =
        new AGMemberDto(
            memberId,
            new com.peppeosmio.lockate.common.dto.EncryptedDataDto("cipher", "iv"),
            groupEntity.getCreatedAt(),
            false,
            Optional.empty());
    when(agMemberMapper.toDto(member)).thenReturn(expectedDto);
    var authentication = new AGMemberAuthentication(UUID.randomUUID());

    var result = service.getMembers(groupId, authentication);

    assertThat(result.members()).containsExactly(expectedDto);
  }

  @Test
  void getMembers_missingGroup_throwsAGNotFoundException() {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.empty());
    var authentication = new AGMemberAuthentication(UUID.randomUUID());

    assertThatThrownBy(() -> service.getMembers(groupId, authentication))
        .isInstanceOf(AGNotFoundException.class);
    verify(agMemberRepository, never()).findMembersWithLastLocation(any());
  }

  // ---------- getMembersCount ----------

  @Test
  void getMembersCount_returnsCountFromRepository() throws Exception {
    when(agMemberRepository.countByAnonymousGroupId(groupId)).thenReturn(5);
    var authentication = new AGMemberAuthentication(UUID.randomUUID());

    var result = service.getMembersCount(groupId, authentication);

    assertThat(result.membersCount()).isEqualTo(5);
  }

  // ---------- relayMemberMessage ----------

  @Test
  void relayMemberMessage_underThrottleInterval_doesNotSaveButAlwaysPublishes() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(agLocationConfigurationProperties.getSaveInterval()).thenReturn(Duration.ofMinutes(2));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    var authentication = new AGMemberAuthentication(memberId);
    var dto =
        new AGMemberMessageReqDto(
            new EncryptedDataDto(
                Base64.getEncoder().encodeToString("coord".getBytes()),
                Base64.getEncoder().encodeToString("iv".getBytes())));
    var lastSaved = LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofSeconds(30));

    var result = service.relayMemberMessage(groupId, authentication, dto, lastSaved);

    assertThat(result).isEmpty();
    verify(agLocationRepository, never()).save(any());
    verify(redisService).publish(eq("ag-" + groupId), eq("{}"));
  }

  @Test
  void relayMemberMessage_atThrottleBoundary_saves() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(agLocationConfigurationProperties.getSaveInterval()).thenReturn(Duration.ofMinutes(2));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(agLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var authentication = new AGMemberAuthentication(memberId);
    var dto =
        new AGMemberMessageReqDto(
            new EncryptedDataDto(
                Base64.getEncoder().encodeToString("coord".getBytes()),
                Base64.getEncoder().encodeToString("iv".getBytes())));
    var lastSaved = LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofMinutes(2));

    var result = service.relayMemberMessage(groupId, authentication, dto, lastSaved);

    assertThat(result).isPresent();
    verify(agLocationRepository).save(any());
  }

  @Test
  void relayMemberMessage_overThrottleInterval_saves() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(agLocationConfigurationProperties.getSaveInterval()).thenReturn(Duration.ofMinutes(2));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(agLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var authentication = new AGMemberAuthentication(memberId);
    var dto =
        new AGMemberMessageReqDto(
            new EncryptedDataDto(
                Base64.getEncoder().encodeToString("coord".getBytes()),
                Base64.getEncoder().encodeToString("iv".getBytes())));
    var lastSaved = LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofMinutes(5));

    var result = service.relayMemberMessage(groupId, authentication, dto, lastSaved);

    assertThat(result).isPresent();
    verify(agLocationRepository).save(any());
  }

  @Test
  void relayMemberMessage_noTimestampProvided_fallsBackToDbLookup() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(agLocationRepository.findFirstByAgMemberEntityOrderByTimestampDescIdDesc(member))
        .thenReturn(Optional.empty());
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(agLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var authentication = new AGMemberAuthentication(memberId);
    var dto =
        new AGMemberMessageReqDto(
            new EncryptedDataDto(
                Base64.getEncoder().encodeToString("coord".getBytes()),
                Base64.getEncoder().encodeToString("iv".getBytes())));

    var result = service.relayMemberMessage(groupId, authentication, dto, null);

    assertThat(result).isPresent();
    verify(agLocationRepository).findFirstByAgMemberEntityOrderByTimestampDescIdDesc(member);
    verify(agLocationRepository).save(any());
  }

  // ---------- circles ----------

  private EncryptedDataDto encryptedData() {
    return new EncryptedDataDto(
        Base64.getEncoder().encodeToString("circle".getBytes()),
        Base64.getEncoder().encodeToString("iv".getBytes()));
  }

  private AGFrameDto publishedFrame() {
    var captor = ArgumentCaptor.forClass(AGFrameDto.class);
    verify(objectMapper).writeValueAsString(captor.capture());
    verify(redisService).publish(eq("ag-" + groupId), any());
    return captor.getValue();
  }

  @Test
  void upsertCircle_newCircle_savesAndPublishesIt() throws Exception {
    var circleId = UUID.randomUUID();
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agCircleRepository.findById(circleId)).thenReturn(Optional.empty());
    when(agCircleRepository.countByAnonymousGroupId(groupId)).thenReturn(0);
    when(agCircleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.upsertCircle(groupId, circleId, encryptedData());

    var frame = publishedFrame();
    assertThat(frame.type()).isEqualTo(AGFrameDto.CIRCLE_UPSERTED);
    assertThat(frame.circle().id()).isEqualTo(circleId);
    assertThat(frame.circle().encryptedData()).isEqualTo(encryptedData());
  }

  @Test
  void upsertCircle_existingCircleOfTheGroup_updatesIt() throws Exception {
    var circleId = UUID.randomUUID();
    var existing =
        new AGCircleEntity(
            circleId,
            new EncryptedString("old".getBytes(), "old-iv".getBytes()),
            groupEntity,
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agCircleRepository.findById(circleId)).thenReturn(Optional.of(existing));

    service.upsertCircle(groupId, circleId, encryptedData());

    assertThat(existing.getDataCipher()).isEqualTo("circle".getBytes());
    assertThat(publishedFrame().type()).isEqualTo(AGFrameDto.CIRCLE_UPSERTED);
    verify(agCircleRepository, never()).save(any());
  }

  @Test
  void upsertCircle_idOfAnotherGroup_throwsConflictAndPublishesNothing() {
    var circleId = UUID.randomUUID();
    var otherGroup =
        new AnonymousGroupEntity(new EncryptedString("other".getBytes(), "other-iv".getBytes()));
    otherGroup.setId(UUID.randomUUID());
    var existing =
        new AGCircleEntity(
            circleId,
            new EncryptedString("old".getBytes(), "old-iv".getBytes()),
            otherGroup,
            LocalDateTime.now(ZoneOffset.UTC));
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agCircleRepository.findById(circleId)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.upsertCircle(groupId, circleId, encryptedData()))
        .isInstanceOf(AGCircleConflictException.class)
        .hasMessage("circle_id_taken");
    verify(redisService, never()).publish(any(), any());
  }

  @Test
  void upsertCircle_groupAtTheLimit_throwsConflict() {
    var circleId = UUID.randomUUID();
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agCircleRepository.findById(circleId)).thenReturn(Optional.empty());
    when(agCircleRepository.countByAnonymousGroupId(groupId))
        .thenReturn(AnonymousGroupService.MAX_CIRCLES_PER_GROUP);

    assertThatThrownBy(() -> service.upsertCircle(groupId, circleId, encryptedData()))
        .isInstanceOf(AGCircleConflictException.class)
        .hasMessage("circle_limit_reached");
    verify(agCircleRepository, never()).save(any());
  }

  @Test
  void upsertCircle_missingGroup_throwsAGNotFound() {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.upsertCircle(groupId, UUID.randomUUID(), encryptedData()))
        .isInstanceOf(AGNotFoundException.class);
  }

  @Test
  void deleteCircle_existingCircle_deletesAndPublishes() throws Exception {
    var circleId = UUID.randomUUID();
    var circle =
        new AGCircleEntity(
            circleId,
            new EncryptedString("c".getBytes(), "iv".getBytes()),
            groupEntity,
            LocalDateTime.now(ZoneOffset.UTC));
    when(agCircleRepository.findByIdAndAnonymousGroupId(circleId, groupId))
        .thenReturn(Optional.of(circle));

    service.deleteCircle(groupId, circleId);

    verify(agCircleRepository).delete(circle);
    var frame = publishedFrame();
    assertThat(frame.type()).isEqualTo(AGFrameDto.CIRCLE_DELETED);
    assertThat(frame.circleId()).isEqualTo(circleId);
  }

  @Test
  void deleteCircle_circleOfAnotherGroup_throwsNotFound() {
    var circleId = UUID.randomUUID();
    when(agCircleRepository.findByIdAndAnonymousGroupId(circleId, groupId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteCircle(groupId, circleId))
        .isInstanceOf(AGCircleNotFoundException.class);
    verify(redisService, never()).publish(any(), any());
  }

  @Test
  void getSnapshot_returnsMembersAndCircles() throws Exception {
    var circle =
        new AGCircleEntity(
            UUID.randomUUID(),
            new EncryptedString("c".getBytes(), "iv".getBytes()),
            groupEntity,
            LocalDateTime.now(ZoneOffset.UTC));
    var memberDto =
        new AGMemberDto(UUID.randomUUID(), encryptedData(), null, false, Optional.empty());
    var member = newMember(memberDto.id(), "t".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findMembersWithLastLocation(groupId)).thenReturn(List.of(member));
    when(agMemberMapper.toDto(member)).thenReturn(memberDto);
    when(agCircleRepository.findByAnonymousGroupIdOrderByCreatedAtAscIdAsc(groupId))
        .thenReturn(List.of(circle));

    var snapshot = service.getSnapshot(groupId);

    assertThat(snapshot.type()).isEqualTo(AGFrameDto.SNAPSHOT);
    assertThat(snapshot.members()).containsExactly(memberDto);
    assertThat(snapshot.circles()).extracting(CircleDto::id).containsExactly(circle.getId());
  }

  @Test
  void getSnapshot_missingGroup_throwsAGNotFound() {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSnapshot(groupId))
        .isInstanceOf(AGNotFoundException.class);
  }

  // ---------- group frames ----------

  @Test
  void deleteMember_publishesMemberLeft() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));

    service.deleteMember(groupId, new AGMemberAuthentication(memberId));

    var frame = publishedFrame();
    assertThat(frame.type()).isEqualTo(AGFrameDto.MEMBER_LEFT);
    assertThat(frame.memberId()).isEqualTo(memberId);
  }

  @Test
  void promoteMember_publishesMemberUpdated() throws Exception {
    var callerId = UUID.randomUUID();
    var targetId = UUID.randomUUID();
    var caller = newMember(callerId, "t".getBytes(), true, groupEntity);
    var target = newMember(targetId, "t".getBytes(), false, groupEntity);
    when(agMemberRepository.findById(callerId)).thenReturn(Optional.of(caller));
    when(agMemberRepository.findByIdAndAnonymousGroupId(targetId, groupId))
        .thenReturn(Optional.of(target));

    service.promoteMember(groupId, new AGMemberAuthentication(callerId), targetId);

    var frame = publishedFrame();
    assertThat(frame.type()).isEqualTo(AGFrameDto.MEMBER_UPDATED);
    assertThat(frame.memberId()).isEqualTo(targetId);
    assertThat(frame.isAGAdmin()).isTrue();
  }

  @Test
  void deleteAnonymousGroupForAdmin_publishesGroupDeleted() throws Exception {
    when(anonymousGroupRepository.existsById(groupId)).thenReturn(true);

    service.deleteAnonymousGroupForAdmin(groupId);

    assertThat(publishedFrame().type()).isEqualTo(AGFrameDto.GROUP_DELETED);
  }

  // ---------- listGroupsForAdmin ----------

  @Test
  void listGroupsForAdmin_groupWithMembers_returnsSummary() {
    var otherGroup =
        new AnonymousGroupEntity(new EncryptedString("other".getBytes(), "other-iv".getBytes()));
    var otherGroupId = UUID.randomUUID();
    otherGroup.setId(otherGroupId);
    var pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
    var pageResult = new PageImpl<>(List.of(groupEntity, otherGroup), pageable, 2);
    when(anonymousGroupRepository.findAll(pageable)).thenReturn(pageResult);
    var lastLocationAt = LocalDateTime.now(ZoneOffset.UTC);
    var summary =
        org.mockito.Mockito.mock(
            com.peppeosmio.lockate.anonymous_group.repository.AGGroupSummaryProjection.class);
    when(summary.getGroupId()).thenReturn(groupId);
    when(summary.getMemberCount()).thenReturn(3L);
    when(summary.getLastLocationAt()).thenReturn(lastLocationAt);
    when(agMemberRepository.summarizeByGroupIds(List.of(groupId, otherGroupId)))
        .thenReturn(List.of(summary));

    var result = service.listGroupsForAdmin(new PageQueryDto(0, 20));

    assertThat(result.items()).hasSize(2);
    assertThat(result.totalElements()).isEqualTo(2);
    assertThat(result.totalPages()).isEqualTo(1);
    var groupSummary =
        result.items().stream().filter(g -> g.id().equals(groupId)).findFirst().orElseThrow();
    assertThat(groupSummary.memberCount()).isEqualTo(3L);
    assertThat(groupSummary.lastLocationAt()).isEqualTo(lastLocationAt);
    var emptyGroupSummary =
        result.items().stream().filter(g -> g.id().equals(otherGroupId)).findFirst().orElseThrow();
    assertThat(emptyGroupSummary.memberCount()).isEqualTo(0L);
    assertThat(emptyGroupSummary.lastLocationAt()).isNull();
  }

  @Test
  void listGroupsForAdmin_noGroups_returnsEmptyPageWithoutQueryingSummaries() {
    var pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
    when(anonymousGroupRepository.findAll(pageable))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    var result = service.listGroupsForAdmin(new PageQueryDto(0, 20));

    assertThat(result.items()).isEmpty();
    assertThat(result.totalElements()).isZero();
    verify(agMemberRepository, never()).summarizeByGroupIds(any());
  }

  // ---------- listMembersForAdmin ----------

  @Test
  void listMembersForAdmin_existingGroup_returnsMembersWithNullableLastLocation() throws Exception {
    var memberWithLocation = newMember(UUID.randomUUID(), "token1".getBytes(), true, groupEntity);
    var location =
        new com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity(
            new EncryptedString("coord".getBytes(), "iv".getBytes()),
            memberWithLocation,
            LocalDateTime.now(ZoneOffset.UTC));
    memberWithLocation.setLastLocation(location);
    var memberWithoutLocation =
        newMember(UUID.randomUUID(), "token2".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.existsById(groupId)).thenReturn(true);
    when(agMemberRepository.findMembersWithLastLocation(groupId))
        .thenReturn(List.of(memberWithLocation, memberWithoutLocation));

    var result = service.listMembersForAdmin(groupId);

    assertThat(result).hasSize(2);
    var withLocation =
        result.stream()
            .filter(m -> m.id().equals(memberWithLocation.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(withLocation.lastLocationAt()).isEqualTo(location.getTimestamp());
    assertThat(withLocation.isAGAdmin()).isTrue();
    var withoutLocation =
        result.stream()
            .filter(m -> m.id().equals(memberWithoutLocation.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(withoutLocation.lastLocationAt()).isNull();
  }

  @Test
  void listMembersForAdmin_missingGroup_throwsAGNotFoundException() {
    when(anonymousGroupRepository.existsById(groupId)).thenReturn(false);

    assertThatThrownBy(() -> service.listMembersForAdmin(groupId))
        .isInstanceOf(AGNotFoundException.class);
    verify(agMemberRepository, never()).findMembersWithLastLocation(any());
  }

  // ---------- deleteAnonymousGroup ----------

  @Test
  void deleteAnonymousGroup_admin_deletesGroup() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), true, groupEntity);
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    var authentication = new AGMemberAuthentication(memberId);

    service.deleteAnonymousGroup(groupId, authentication);

    verify(anonymousGroupRepository).deleteAnonymousGroup(groupId);
  }

  @Test
  void deleteAnonymousGroup_nonAdmin_throwsAGMemberNotAdminException() {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    var authentication = new AGMemberAuthentication(memberId);

    assertThatThrownBy(() -> service.deleteAnonymousGroup(groupId, authentication))
        .isInstanceOf(AGMemberNotAdminException.class);
    verify(anonymousGroupRepository, never()).deleteAnonymousGroup(any());
  }

  // ---------- promoteMember ----------

  @Test
  void promoteMember_admin_promotesTarget() throws Exception {
    var adminId = UUID.randomUUID();
    var targetId = UUID.randomUUID();
    var admin = newMember(adminId, "token".getBytes(), true, groupEntity);
    var target = newMember(targetId, "token".getBytes(), false, groupEntity);
    when(agMemberRepository.findById(adminId)).thenReturn(Optional.of(admin));
    when(agMemberRepository.findByIdAndAnonymousGroupId(targetId, groupId))
        .thenReturn(Optional.of(target));

    service.promoteMember(groupId, new AGMemberAuthentication(adminId), targetId);

    assertThat(target.isAGAdmin()).isTrue();
  }

  @Test
  void promoteMember_targetAlreadyAdmin_succeeds() throws Exception {
    var adminId = UUID.randomUUID();
    var targetId = UUID.randomUUID();
    var admin = newMember(adminId, "token".getBytes(), true, groupEntity);
    var target = newMember(targetId, "token".getBytes(), true, groupEntity);
    when(agMemberRepository.findById(adminId)).thenReturn(Optional.of(admin));
    when(agMemberRepository.findByIdAndAnonymousGroupId(targetId, groupId))
        .thenReturn(Optional.of(target));

    service.promoteMember(groupId, new AGMemberAuthentication(adminId), targetId);

    assertThat(target.isAGAdmin()).isTrue();
  }

  @Test
  void promoteMember_nonAdmin_throwsAGMemberNotAdminException() {
    var callerId = UUID.randomUUID();
    var targetId = UUID.randomUUID();
    var caller = newMember(callerId, "token".getBytes(), false, groupEntity);
    when(agMemberRepository.findById(callerId)).thenReturn(Optional.of(caller));

    assertThatThrownBy(
            () -> service.promoteMember(groupId, new AGMemberAuthentication(callerId), targetId))
        .isInstanceOf(AGMemberNotAdminException.class);
    verify(agMemberRepository, never()).findByIdAndAnonymousGroupId(any(), any());
  }

  @Test
  void promoteMember_targetNotInGroup_throwsAGMemberNotFoundException() {
    var adminId = UUID.randomUUID();
    var targetId = UUID.randomUUID();
    var admin = newMember(adminId, "token".getBytes(), true, groupEntity);
    when(agMemberRepository.findById(adminId)).thenReturn(Optional.of(admin));
    when(agMemberRepository.findByIdAndAnonymousGroupId(targetId, groupId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.promoteMember(groupId, new AGMemberAuthentication(adminId), targetId))
        .isInstanceOf(AGMemberNotFoundException.class);
  }

  @Test
  void promoteMember_unknownCaller_throwsUnauthorizedException() {
    var callerId = UUID.randomUUID();
    when(agMemberRepository.findById(callerId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.promoteMember(
                    groupId, new AGMemberAuthentication(callerId), UUID.randomUUID()))
        .isInstanceOf(UnauthorizedException.class);
  }

  // ---------- deleteAnonymousGroupForAdmin ----------

  @Test
  void deleteAnonymousGroupForAdmin_existingGroup_deletesGroup() throws Exception {
    when(anonymousGroupRepository.existsById(groupId)).thenReturn(true);

    service.deleteAnonymousGroupForAdmin(groupId);

    verify(anonymousGroupRepository).deleteAnonymousGroup(groupId);
  }

  @Test
  void deleteAnonymousGroupForAdmin_missingGroup_throwsAGNotFoundException() {
    when(anonymousGroupRepository.existsById(groupId)).thenReturn(false);

    assertThatThrownBy(() -> service.deleteAnonymousGroupForAdmin(groupId))
        .isInstanceOf(AGNotFoundException.class);
    verify(anonymousGroupRepository, never()).deleteAnonymousGroup(any());
  }

  // ---------- deleteMemberForAdmin ----------

  @Test
  void deleteMemberForAdmin_existingMember_deletesMember() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(agMemberRepository.findByIdAndAnonymousGroupId(memberId, groupId))
        .thenReturn(Optional.of(member));

    service.deleteMemberForAdmin(groupId, memberId);

    verify(agMemberRepository).deleteById(memberId);
  }

  @Test
  void deleteMemberForAdmin_missingMember_throwsAGMemberNotFoundException() {
    var memberId = UUID.randomUUID();
    when(agMemberRepository.findByIdAndAnonymousGroupId(memberId, groupId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteMemberForAdmin(groupId, memberId))
        .isInstanceOf(AGMemberNotFoundException.class);
    verify(agMemberRepository, never()).deleteById(any());
  }

  // ---------- setMemberAdminForAdmin ----------

  @Test
  void setMemberAdminForAdmin_promote_setsAdminFlag() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(agMemberRepository.findByIdAndAnonymousGroupId(memberId, groupId))
        .thenReturn(Optional.of(member));

    service.setMemberAdminForAdmin(groupId, memberId, true);

    assertThat(member.isAGAdmin()).isTrue();
  }

  @Test
  void setMemberAdminForAdmin_demote_clearsAdminFlag() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), true, groupEntity);
    when(agMemberRepository.findByIdAndAnonymousGroupId(memberId, groupId))
        .thenReturn(Optional.of(member));

    service.setMemberAdminForAdmin(groupId, memberId, false);

    assertThat(member.isAGAdmin()).isFalse();
  }

  @Test
  void setMemberAdminForAdmin_missingMember_throwsAGMemberNotFoundException() {
    var memberId = UUID.randomUUID();
    when(agMemberRepository.findByIdAndAnonymousGroupId(memberId, groupId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.setMemberAdminForAdmin(groupId, memberId, true))
        .isInstanceOf(AGMemberNotFoundException.class);
  }
}
