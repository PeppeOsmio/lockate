package com.peppeosmio.lockate.anonymous_group.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.jobs.JobRunService.Job;
import com.peppeosmio.lockate.anonymous_group.jobs.JobRunService.JobRun;
import com.peppeosmio.lockate.redis.RedisService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class JobRunServiceTest {

  @Mock private RedisService redisService;

  private JobRunService service;

  @BeforeEach
  void setUp() {
    service = new JobRunService(redisService);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void recorded_keepsTheTimeAndCountWithoutExpiry() {
    var before = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);

    service.recorded(Job.LOCATION_SAVE, 3);

    var value = ArgumentCaptor.forClass(String.class);
    verify(redisService).setValue(eq("job-run:location-save"), value.capture());
    var parts = value.getValue().split("\\|");
    assertThat(LocalDateTime.parse(parts[0])).isAfterOrEqualTo(before);
    assertThat(parts[1]).isEqualTo("3");
  }

  @Test
  void recorded_inATransaction_waitsForTheCommit() {
    TransactionSynchronizationManager.initSynchronization();

    service.recorded(Job.LOCATION_RETENTION, 10);

    verify(redisService, never()).setValue(anyString(), any());
    TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
    verify(redisService).setValue(eq("job-run:location-retention"), anyString());
  }

  @Test
  void last_readsTheRecordedRun() {
    when(redisService.getValue("job-run:location-save"))
        .thenReturn(Optional.of("2026-11-21T10:00|7"));

    assertThat(service.last(Job.LOCATION_SAVE))
        .contains(new JobRun(LocalDateTime.of(2026, 11, 21, 10, 0), 7));
  }

  @Test
  void last_isEmptyWhenTheJobNeverRan() {
    when(redisService.getValue("job-run:location-retention")).thenReturn(Optional.empty());

    assertThat(service.last(Job.LOCATION_RETENTION)).isEmpty();
  }
}
