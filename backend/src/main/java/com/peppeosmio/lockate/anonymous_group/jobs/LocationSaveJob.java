package com.peppeosmio.lockate.anonymous_group.jobs;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import com.peppeosmio.lockate.anonymous_group.location.AGLastLocationService;
import com.peppeosmio.lockate.anonymous_group.repository.AGLocationRepository;
import com.peppeosmio.lockate.anonymous_group.repository.AGMemberRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Saves the last position of each member that sent since the previous run. */
@Component
public class LocationSaveJob {

  private final AGLastLocationService lastLocationService;
  private final AGMemberRepository agMemberRepository;
  private final AGLocationRepository agLocationRepository;

  public LocationSaveJob(
      AGLastLocationService lastLocationService,
      AGMemberRepository agMemberRepository,
      AGLocationRepository agLocationRepository) {
    this.lastLocationService = lastLocationService;
    this.agMemberRepository = agMemberRepository;
    this.agLocationRepository = agLocationRepository;
  }

  @Scheduled(fixedDelayString = "${lockate.retention.anonymous-group.location.save-interval}")
  @Transactional
  public void saveLastLocations() {
    for (var unsaved : lastLocationService.takeUnsaved()) {
      var record = unsaved.record();
      // a member who left is gone; a record taken again after a post that came in between is
      // already saved
      agMemberRepository
          .findById(unsaved.memberId())
          .filter(
              member ->
                  member.getLastLocation() == null
                      || record.timestamp().isAfter(member.getLastLocation().getTimestamp()))
          .ifPresent(
              member ->
                  member.setLastLocation(
                      agLocationRepository.save(
                          new AGMemberLocationEntity(
                              record.encryptedCoordinates().toEncryptedString(),
                              record.encryptedActivity().toEncryptedString(),
                              member,
                              record.timestamp()))));
    }
  }
}
