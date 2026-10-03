package com.peppeosmio.lockate.anonymous_group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.dto.*;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotAdminException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGMemberNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.AGNotFoundException;
import com.peppeosmio.lockate.anonymous_group.exceptions.Base64Exception;
import com.peppeosmio.lockate.anonymous_group.mapper.AGMemberMapper;
import com.peppeosmio.lockate.anonymous_group.mapper.AnonymousGroupMapper;
import com.peppeosmio.lockate.anonymous_group.repository.AGLocationRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AnonymousGroupRepository;
import com.peppeosmio.lockate.anonymous_group.security.AGMemberAuthentication;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import com.peppeosmio.lockate.common.dto.PageQueryDto;
import com.peppeosmio.lockate.common.exceptions.UnauthorizedException;
import com.peppeosmio.lockate.redis.RedisService;
import com.peppeosmio.lockate.srp.SrpService;
import com.peppeosmio.lockate.srp.SrpSession;
import com.peppeosmio.lockate.srp.SrpSessionResult;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bouncycastle.crypto.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.security.crypto.bcrypt.BCrypt;

@ExtendWith(MockitoExtension.class)
class AnonymousGroupServiceTest {

  @Mock private AGLocationConfigurationProperties agLocationConfigurationProperties;
  @Mock private AnonymousGroupRepository anonymousGroupRepository;
  @Mock private AGMemberRepository agMemberRepository;
  @Mock private AGLocationRepository agLocationRepository;
  @Mock private RedisService redisService;
  @Mock private SrpService srpService;
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
            redisService,
            srpService,
            objectMapper,
            anonymousGroupMapper,
            agMemberMapper);

    groupEntity =
        new AnonymousGroupEntity(
            new EncryptedString("group-name".getBytes(), "group-iv".getBytes()),
            BigInteger.valueOf(123456789L).toByteArray(),
            "salt".getBytes(),
            "keysalt".getBytes());
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
  void authenticateMember_memberBelongsToDifferentGroup_throwsUnauthorized() {
    var otherGroup =
        new AnonymousGroupEntity(
            new EncryptedString("other".getBytes(), "other-iv".getBytes()),
            "verifier".getBytes(),
            "salt".getBytes(),
            "keysalt".getBytes());
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
                encoder.encodeToString("giv".getBytes())),
            encoder.encodeToString("verifier".getBytes()),
            encoder.encodeToString("salt".getBytes()),
            encoder.encodeToString("keysalt".getBytes()));

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

  // ---------- getMemberSrpInfo ----------

  @Test
  void getMemberSrpInfo_existingGroup_returnsEncryptedNameAndSalts() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));

    var result = service.getMemberSrpInfo(groupId);

    assertThat(result.encryptedName().cipherText())
        .isEqualTo(Base64.getEncoder().encodeToString("group-name".getBytes()));
    assertThat(result.keySalt())
        .isEqualTo(Base64.getEncoder().encodeToString("keysalt".getBytes()));
  }

  @Test
  void getMemberSrpInfo_missingGroup_throwsAGNotFoundException() {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMemberSrpInfo(groupId))
        .isInstanceOf(AGNotFoundException.class);
  }

  // ---------- startMemberSrpAuth ----------

  @Test
  void startMemberSrpAuth_success_returnsSessionAndB() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    var session =
        new SrpSession("encoded-A", "encoded-b", "encoded-B", LocalDateTime.now(ZoneOffset.UTC));
    when(srpService.startSrp(any(), any())).thenReturn(new SrpSessionResult("srp:abc", session));
    var dto = new AGMemberAuthStartReqDto(Base64.getEncoder().encodeToString("A".getBytes()));

    var result = service.startMemberSrpAuth(groupId, dto);

    assertThat(result.srpSessionId()).isEqualTo("srp:abc");
    assertThat(result.B()).isEqualTo("encoded-B");
  }

  @Test
  void startMemberSrpAuth_cryptoException_throwsUnauthorized() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(srpService.startSrp(any(), any())).thenThrow(new CryptoException("bad"));
    var dto = new AGMemberAuthStartReqDto(Base64.getEncoder().encodeToString("A".getBytes()));

    assertThatThrownBy(() -> service.startMemberSrpAuth(groupId, dto))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void startMemberSrpAuth_invalidBase64_throwsBase64Exception() {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    var dto = new AGMemberAuthStartReqDto("not-valid-base64!!!");

    assertThatThrownBy(() -> service.startMemberSrpAuth(groupId, dto))
        .isInstanceOf(Base64Exception.class);
  }

  // ---------- verifyMemberSrpAuth ----------

  @Test
  void verifyMemberSrpAuth_success_createsNonAdminMemberAndReturnsMembers() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(srpService.verifySrp(any(), any(), any())).thenReturn(true);
    when(agMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(agMemberRepository.findMembersWithLastLocation(groupId)).thenReturn(List.of());
    var encoder = Base64.getEncoder();
    var dto =
        new AGMemberAuthVerifyReqDto(
            new EncryptedDataDto(
                encoder.encodeToString("member".getBytes()),
                encoder.encodeToString("miv".getBytes())),
            "srp:abc",
            encoder.encodeToString("M1".getBytes()));

    var result = service.verifyMemberSrpAuth(groupId, dto);

    var memberCaptor = ArgumentCaptor.forClass(AGMemberEntity.class);
    verify(agMemberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().isAGAdmin()).isFalse();
    assertThat(result.members()).isEmpty();
  }

  @Test
  void verifyMemberSrpAuth_wrongPassword_throwsUnauthorized() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(srpService.verifySrp(any(), any(), any())).thenReturn(false);
    var encoder = Base64.getEncoder();
    var dto =
        new AGMemberAuthVerifyReqDto(
            new EncryptedDataDto(
                encoder.encodeToString("member".getBytes()),
                encoder.encodeToString("miv".getBytes())),
            "srp:abc",
            encoder.encodeToString("M1".getBytes()));

    assertThatThrownBy(() -> service.verifyMemberSrpAuth(groupId, dto))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void verifyMemberSrpAuth_cryptoException_throwsUnauthorized() throws Exception {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(srpService.verifySrp(any(), any(), any())).thenThrow(new CryptoException("bad"));
    var encoder = Base64.getEncoder();
    var dto =
        new AGMemberAuthVerifyReqDto(
            new EncryptedDataDto(
                encoder.encodeToString("member".getBytes()),
                encoder.encodeToString("miv".getBytes())),
            "srp:abc",
            encoder.encodeToString("M1".getBytes()));

    assertThatThrownBy(() -> service.verifyMemberSrpAuth(groupId, dto))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void verifyMemberSrpAuth_invalidBase64M1_throwsBase64Exception() {
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    var encoder = Base64.getEncoder();
    var dto =
        new AGMemberAuthVerifyReqDto(
            new EncryptedDataDto(
                encoder.encodeToString("member".getBytes()),
                encoder.encodeToString("miv".getBytes())),
            "srp:abc",
            "not-valid-base64!!!");

    assertThatThrownBy(() -> service.verifyMemberSrpAuth(groupId, dto))
        .isInstanceOf(Base64Exception.class);
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
        new AnonymousGroupEntity(
            new EncryptedString("other".getBytes(), "other-iv".getBytes()),
            "verifier".getBytes(),
            "salt".getBytes(),
            "keysalt".getBytes());
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

  // ---------- saveLocation ----------

  @Test
  void saveLocation_underThrottleInterval_doesNotSaveButAlwaysPublishes() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(agLocationConfigurationProperties.getSaveInterval()).thenReturn(Duration.ofMinutes(2));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    var authentication = new AGMemberAuthentication(memberId);
    var dto =
        new AGLocationSaveReqDto(
            new EncryptedDataDto(
                Base64.getEncoder().encodeToString("coord".getBytes()),
                Base64.getEncoder().encodeToString("iv".getBytes())));
    var lastSaved = LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofSeconds(30));

    var result = service.saveLocation(groupId, authentication, dto, lastSaved);

    assertThat(result).isEmpty();
    verify(agLocationRepository, never()).save(any());
    verify(redisService).publish(eq("ag-" + groupId), eq("{}"));
  }

  @Test
  void saveLocation_atThrottleBoundary_saves() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(agLocationConfigurationProperties.getSaveInterval()).thenReturn(Duration.ofMinutes(2));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(agLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var authentication = new AGMemberAuthentication(memberId);
    var dto =
        new AGLocationSaveReqDto(
            new EncryptedDataDto(
                Base64.getEncoder().encodeToString("coord".getBytes()),
                Base64.getEncoder().encodeToString("iv".getBytes())));
    var lastSaved = LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofMinutes(2));

    var result = service.saveLocation(groupId, authentication, dto, lastSaved);

    assertThat(result).isPresent();
    verify(agLocationRepository).save(any());
  }

  @Test
  void saveLocation_overThrottleInterval_saves() throws Exception {
    var memberId = UUID.randomUUID();
    var member = newMember(memberId, "token".getBytes(), false, groupEntity);
    when(anonymousGroupRepository.findById(groupId)).thenReturn(Optional.of(groupEntity));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(agLocationConfigurationProperties.getSaveInterval()).thenReturn(Duration.ofMinutes(2));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    when(agLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var authentication = new AGMemberAuthentication(memberId);
    var dto =
        new AGLocationSaveReqDto(
            new EncryptedDataDto(
                Base64.getEncoder().encodeToString("coord".getBytes()),
                Base64.getEncoder().encodeToString("iv".getBytes())));
    var lastSaved = LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofMinutes(5));

    var result = service.saveLocation(groupId, authentication, dto, lastSaved);

    assertThat(result).isPresent();
    verify(agLocationRepository).save(any());
  }

  @Test
  void saveLocation_noTimestampProvided_fallsBackToDbLookup() throws Exception {
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
        new AGLocationSaveReqDto(
            new EncryptedDataDto(
                Base64.getEncoder().encodeToString("coord".getBytes()),
                Base64.getEncoder().encodeToString("iv".getBytes())));

    var result = service.saveLocation(groupId, authentication, dto, null);

    assertThat(result).isPresent();
    verify(agLocationRepository).findFirstByAgMemberEntityOrderByTimestampDescIdDesc(member);
    verify(agLocationRepository).save(any());
  }

  // ---------- streamLocations ----------

  @Test
  void streamLocations_filtersOutAuthorsOwnUpdatesAndUnsubscribeWorks() throws Exception {
    var memberId = UUID.randomUUID();
    var otherMemberId = UUID.randomUUID();
    var authentication = new AGMemberAuthentication(memberId);
    var listener = org.mockito.Mockito.mock(MessageListener.class);
    var channel = "ag-" + groupId;
    var consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    when(redisService.subscribe(eq(channel), consumerCaptor.capture())).thenReturn(listener);
    var ownUpdate = new LocationUpdateDto(null, memberId);
    var otherUpdate = new LocationUpdateDto(null, otherMemberId);
    when(objectMapper.readValue("own-message", LocationUpdateDto.class)).thenReturn(ownUpdate);
    when(objectMapper.readValue("other-message", LocationUpdateDto.class)).thenReturn(otherUpdate);
    java.util.List<LocationUpdateDto> received = new java.util.ArrayList<>();

    var unsubscribe = service.streamLocations(groupId, received::add, authentication);
    @SuppressWarnings("unchecked")
    Consumer<String> capturedConsumer = consumerCaptor.getValue();
    capturedConsumer.accept("own-message");
    capturedConsumer.accept("other-message");

    assertThat(received).containsExactly(otherUpdate);

    unsubscribe.run();
    verify(redisService).unsubscribe(channel, listener);
  }

  // ---------- listGroupsForAdmin ----------

  @Test
  void listGroupsForAdmin_groupWithMembers_returnsSummary() {
    var otherGroup =
        new AnonymousGroupEntity(
            new EncryptedString("other".getBytes(), "other-iv".getBytes()),
            "verifier".getBytes(),
            "salt".getBytes(),
            "keysalt".getBytes());
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
}
