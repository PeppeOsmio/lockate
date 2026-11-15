package com.peppeosmio.lockate.anonymous_group.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.dto.LocationRecordDto;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import com.peppeosmio.lockate.anonymous_group.location.AGLastLocationService;
import com.peppeosmio.lockate.anonymous_group.repository.AGLocationRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import com.peppeosmio.lockate.common.classes.EncryptedString;
import com.peppeosmio.lockate.common.dto.EncryptedDataDto;
import java.time.LocalDateTime;
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

@ExtendWith(MockitoExtension.class)
class LocationSaveJobTest {

  @Mock private AGLastLocationService lastLocationService;
  @Mock private AGMemberRepository agMemberRepository;
  @Mock private AGLocationRepository agLocationRepository;

  private LocationSaveJob job;

  private final UUID memberId = UUID.randomUUID();
  private final LocalDateTime timestamp = LocalDateTime.of(2026, 11, 15, 12, 0);
  private AGMemberEntity member;

  @BeforeEach
  void setUp() {
    job = new LocationSaveJob(lastLocationService, agMemberRepository, agLocationRepository);
    var group =
        new AnonymousGroupEntity(
            UUID.randomUUID(), new EncryptedString("name".getBytes(), "iv".getBytes()));
    member =
        new AGMemberEntity(
            memberId,
            new EncryptedString("name".getBytes(), "iv".getBytes()),
            "token".getBytes(),
            false,
            group);
  }

  private static EncryptedDataDto blob(int bytes) {
    var encoder = Base64.getEncoder();
    return new EncryptedDataDto(
        encoder.encodeToString(new byte[bytes]), encoder.encodeToString(new byte[12]));
  }

  private LocationRecordDto recordAt(LocalDateTime at) {
    return new LocationRecordDto(blob(24), blob(17), at);
  }

  private AGMemberLocationEntity savedAt(LocalDateTime at) throws Exception {
    return new AGMemberLocationEntity(
        recordAt(at).encryptedCoordinates().toEncryptedString(),
        recordAt(at).encryptedActivity().toEncryptedString(),
        member,
        at);
  }

  @Test
  void saveLastLocations_savesTheNewestRecordAsTheMembersLastLocation() {
    when(lastLocationService.takeUnsaved())
        .thenReturn(List.of(new AGLastLocationService.Unsaved(memberId, recordAt(timestamp))));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(agLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    job.saveLastLocations();

    var captor = ArgumentCaptor.forClass(AGMemberLocationEntity.class);
    verify(agLocationRepository).save(captor.capture());
    assertThat(captor.getValue().getTimestamp()).isEqualTo(timestamp);
    assertThat(member.getLastLocation()).isSameAs(captor.getValue());
  }

  @Test
  void saveLastLocations_aRecordAlreadySaved_isNotSavedAgain() throws Exception {
    member.setLastLocation(savedAt(timestamp));
    when(lastLocationService.takeUnsaved())
        .thenReturn(List.of(new AGLastLocationService.Unsaved(memberId, recordAt(timestamp))));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.of(member));

    job.saveLastLocations();

    verify(agLocationRepository, never()).save(any());
  }

  @Test
  void saveLastLocations_aMemberWhoLeft_isSkipped() {
    when(lastLocationService.takeUnsaved())
        .thenReturn(List.of(new AGLastLocationService.Unsaved(memberId, recordAt(timestamp))));
    when(agMemberRepository.findById(memberId)).thenReturn(Optional.empty());

    job.saveLastLocations();

    verify(agLocationRepository, never()).save(any());
  }
}
