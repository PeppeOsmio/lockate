package com.peppeosmio.lockate.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.admin.dto.AdminOverviewDto.Counts;
import com.peppeosmio.lockate.admin.dto.AdminOverviewDto.JobStatus;
import com.peppeosmio.lockate.admin.repository.AdminOverviewRepository;
import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.jobs.JobRunService;
import com.peppeosmio.lockate.anonymous_group.jobs.JobRunService.Job;
import com.peppeosmio.lockate.anonymous_group.jobs.JobRunService.JobRun;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminOverviewServiceTest {

  @Mock private AdminOverviewRepository repository;
  @Mock private AGPresenceService presenceService;
  @Mock private JobRunService jobRunService;

  private final AGLocationConfigurationProperties locationProperties =
      new AGLocationConfigurationProperties();
  private final Counts counts = new Counts(4, 9, 6, 3, 1, 2, 120, 5);
  private AdminOverviewService service;
  private LocalDateTime now;

  @BeforeEach
  void setUp() {
    locationProperties.setDuration(Duration.ofDays(7));
    locationProperties.setSaveInterval(Duration.ofMinutes(2));
    locationProperties.setCron("0 0 0 * * *");
    service = newService("https://relay.example");
    now = LocalDateTime.now(ZoneOffset.UTC);
    when(repository.counts(any())).thenReturn(counts);
    when(presenceService.onlineCount()).thenReturn(2L);
    when(jobRunService.last(Job.LOCATION_SAVE)).thenReturn(Optional.of(new JobRun(now, 3)));
    when(jobRunService.last(Job.LOCATION_RETENTION)).thenReturn(Optional.of(new JobRun(now, 0)));
  }

  private AdminOverviewService newService(String relayUrl) {
    return new AdminOverviewService(
        repository, presenceService, jobRunService, locationProperties, "0.9.0", true, relayUrl);
  }

  @Test
  void overview_givesTheServersSetupCountsAndJobs() {
    var overview = service.overview(now);

    assertThat(overview.version()).isEqualTo("0.9.0");
    assertThat(overview.apis()).isEqualTo(List.of(1));
    assertThat(overview.requireApiKey()).isTrue();
    assertThat(overview.pushRelayUrl()).isEqualTo("https://relay.example");
    assertThat(overview.locationRetentionSeconds()).isEqualTo(7 * 24 * 3600);
    assertThat(overview.locationSaveIntervalSeconds()).isEqualTo(120);
    assertThat(overview.locationRetentionCron()).isEqualTo("0 0 0 * * *");
    assertThat(overview.databaseUp()).isTrue();
    assertThat(overview.redisUp()).isTrue();
    assertThat(overview.counts()).isEqualTo(counts);
    assertThat(overview.onlineMembers()).isEqualTo(2);
    assertThat(overview.locationSave()).isEqualTo(new JobStatus(now, 3L, false));
    assertThat(overview.locationRetention()).isEqualTo(new JobStatus(now, 0L, false));
  }

  @Test
  void overview_countsInactiveGroupsSince30DaysAgo() {
    service.overview(now);

    var since = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(repository).counts(since.capture());
    assertThat(since.getValue()).isEqualTo(now.minusDays(AdminOverviewService.INACTIVE_DAYS));
  }

  @Test
  void overview_withoutARelay_hasNoRelayUrl() {
    assertThat(newService("").overview(now).pushRelayUrl()).isNull();
  }

  @Test
  void overview_databaseDown_stillAnswersWithoutCounts() {
    when(repository.counts(any())).thenThrow(new DataAccessResourceFailureException("down"));

    var overview = service.overview(now);

    assertThat(overview.databaseUp()).isFalse();
    assertThat(overview.counts()).isNull();
    assertThat(overview.redisUp()).isTrue();
    assertThat(overview.onlineMembers()).isEqualTo(2);
  }

  @Test
  void overview_redisDown_stillAnswersWithoutWhatRedisKnows() {
    when(presenceService.onlineCount()).thenThrow(new RedisConnectionFailureException("down"));

    var overview = service.overview(now);

    assertThat(overview.redisUp()).isFalse();
    assertThat(overview.onlineMembers()).isNull();
    assertThat(overview.locationSave()).isNull();
    assertThat(overview.locationRetention()).isNull();
    assertThat(overview.counts()).isEqualTo(counts);
  }

  @Test
  void overview_aStoreThatDoesntAnswerInTime_countsAsDown() {
    service.storeTimeout = Duration.ofMillis(100);
    when(presenceService.onlineCount())
        .thenAnswer(
            inv -> {
              Thread.sleep(5_000);
              return 2L;
            });

    var overview = service.overview(now);

    assertThat(overview.redisUp()).isFalse();
    assertThat(overview.databaseUp()).isTrue();
  }

  @Test
  void overview_saveJobNotRunFor3Intervals_isOverdue() {
    when(jobRunService.last(Job.LOCATION_SAVE))
        .thenReturn(Optional.of(new JobRun(now.minusMinutes(7), 0)));

    assertThat(service.overview(now).locationSave().overdue()).isTrue();
  }

  @Test
  void overview_jobsThatNeverRan_areOverdueOnlyOnceTheyShouldHave() {
    when(jobRunService.last(any())).thenReturn(Optional.empty());

    var justStarted = service.overview(now);
    var aDayLater = service.overview(now.plusHours(26));

    assertThat(justStarted.locationSave()).isEqualTo(new JobStatus(null, null, false));
    assertThat(justStarted.locationRetention().overdue()).isFalse();
    assertThat(aDayLater.locationSave().overdue()).isTrue();
    assertThat(aDayLater.locationRetention().overdue()).isTrue();
  }

  @Test
  void overview_retentionJobMissingItsCronRunByAnHour_isOverdue() {
    when(jobRunService.last(Job.LOCATION_RETENTION))
        .thenReturn(Optional.of(new JobRun(now.minusHours(26), 0)));

    assertThat(service.overview(now).locationRetention().overdue()).isTrue();
  }
}
