package com.peppeosmio.lockate.admin.service;

import com.peppeosmio.lockate.admin.dto.AdminOverviewDto;
import com.peppeosmio.lockate.admin.dto.AdminOverviewDto.JobStatus;
import com.peppeosmio.lockate.admin.repository.AdminOverviewRepository;
import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.jobs.JobRunService;
import com.peppeosmio.lockate.anonymous_group.jobs.JobRunService.Job;
import com.peppeosmio.lockate.anonymous_group.jobs.JobRunService.JobRun;
import com.peppeosmio.lockate.anonymous_group.presence.AGPresenceService;
import com.peppeosmio.lockate.version.VersionController;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * The server at a glance: its version and setup, whether the database and Redis answer, what they
 * hold and when the jobs last ran. Both stores are asked at once, and one that is down or slow
 * leaves out only what it knows.
 */
@Slf4j
@Service
public class AdminOverviewService {
  static final int INACTIVE_DAYS = 30;
  // A save run is late after missing two; a retention run an hour after its cron time
  private static final int MISSED_SAVES = 3;
  private static final Duration RETENTION_GRACE = Duration.ofHours(1);

  private final AdminOverviewRepository repository;
  private final AGPresenceService presenceService;
  private final JobRunService jobRunService;
  private final AGLocationConfigurationProperties locationProperties;
  private final String version;
  private final boolean requireApiKey;
  private final String pushRelayUrl;
  private final LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  // A store that is down can block a query for its whole timeout (seconds, a minute for Redis)
  Duration storeTimeout = Duration.ofSeconds(5);

  public AdminOverviewService(
      AdminOverviewRepository repository,
      AGPresenceService presenceService,
      JobRunService jobRunService,
      AGLocationConfigurationProperties locationProperties,
      @Value("${lockate.version}") String version,
      @Value("${lockate.require-api-key}") boolean requireApiKey,
      @Value("${lockate.push.relay-url}") String pushRelayUrl) {
    this.repository = repository;
    this.presenceService = presenceService;
    this.jobRunService = jobRunService;
    this.locationProperties = locationProperties;
    this.version = version;
    this.requireApiKey = requireApiKey;
    this.pushRelayUrl = pushRelayUrl.isBlank() ? null : pushRelayUrl;
  }

  public AdminOverviewDto overview() {
    return overview(LocalDateTime.now(ZoneOffset.UTC));
  }

  AdminOverviewDto overview(LocalDateTime now) {
    var countsTask = executor.submit(() -> repository.counts(now.minusDays(INACTIVE_DAYS)));
    var redisTask =
        executor.submit(
            () ->
                new RedisPart(
                    presenceService.onlineCount(),
                    saveStatus(jobRunService.last(Job.LOCATION_SAVE), now),
                    retentionStatus(jobRunService.last(Job.LOCATION_RETENTION), now)));
    var counts = await(countsTask, "The database");
    var redis = await(redisTask, "Redis");
    var online = redis == null ? null : redis.online();
    var save = redis == null ? null : redis.save();
    var retention = redis == null ? null : redis.retention();
    return new AdminOverviewDto(
        version,
        VersionController.SERVED_APIS,
        startedAt,
        requireApiKey,
        pushRelayUrl,
        locationProperties.getDuration().toSeconds(),
        locationProperties.getSaveInterval().toSeconds(),
        locationProperties.getCron(),
        counts != null,
        redis != null,
        counts,
        online,
        save,
        retention);
  }

  private record RedisPart(long online, JobStatus save, JobStatus retention) {}

  // Null when the store fails or doesn't answer in time
  private <T> T await(Future<T> task, String store) {
    try {
      return task.get(storeTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException | TimeoutException e) {
      task.cancel(true);
      log.warn("[Admin] {} didn't answer: {}", store, e.getCause() == null ? e : e.getCause());
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      task.cancel(true);
      return null;
    }
  }

  private JobStatus saveStatus(Optional<JobRun> last, LocalDateTime now) {
    var since = last.map(JobRun::at).orElse(startedAt);
    var late = since.plus(locationProperties.getSaveInterval().multipliedBy(MISSED_SAVES));
    return status(last, late.isBefore(now));
  }

  // The cron runs in the server's time zone
  private JobStatus retentionStatus(Optional<JobRun> last, LocalDateTime now) {
    var zone = ZoneId.systemDefault();
    var since = last.map(JobRun::at).orElse(startedAt).atZone(ZoneOffset.UTC);
    var next =
        CronExpression.parse(locationProperties.getCron()).next(since.withZoneSameInstant(zone));
    var late = next != null && next.plus(RETENTION_GRACE).isBefore(now.atZone(ZoneOffset.UTC));
    return status(last, late);
  }

  private static JobStatus status(Optional<JobRun> last, boolean overdue) {
    return new JobStatus(
        last.map(JobRun::at).orElse(null), last.map(JobRun::count).orElse(null), overdue);
  }
}
