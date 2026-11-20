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
  private final JobRunService jobRunService;

  public LocationSaveJob(
      AGLastLocationService lastLocationService,
      AGMemberRepository agMemberRepository,
      AGLocationRepository agLocationRepository,
      JobRunService jobRunService) {
    this.lastLocationService = lastLocationService;
    this.agMemberRepository = agMemberRepository;
    this.agLocationRepository = agLocationRepository;
    this.jobRunService = jobRunService;
  }

  @Scheduled(fixedDelayString = "${lockate.retention.anonymous-group.location.save-interval}")
  @Transactional
  public void saveLastLocations() {
    var saved = 0;
    for (var unsaved : lastLocationService.takeUnsaved()) {
      var record = unsaved.record();
      // a member who left is gone; a record taken again after a post that came in between is
      // already saved
      var member =
          agMemberRepository
              .findById(unsaved.memberId())
              .filter(
                  m ->
                      m.getLastLocation() == null
                          || record.timestamp().isAfter(m.getLastLocation().getTimestamp()));
      if (member.isPresent()) {
        member
            .get()
            .setLastLocation(
                agLocationRepository.save(
                    new AGMemberLocationEntity(
                        record.encryptedCoordinates().toEncryptedString(),
                        record.encryptedActivity().toEncryptedString(),
                        member.get(),
                        record.timestamp())));
        saved++;
      }
    }
    jobRunService.recorded(JobRunService.Job.LOCATION_SAVE, saved);
  }
}
