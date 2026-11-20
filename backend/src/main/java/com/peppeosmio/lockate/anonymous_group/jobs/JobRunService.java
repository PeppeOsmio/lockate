package com.peppeosmio.lockate.anonymous_group.jobs;

import com.peppeosmio.lockate.redis.RedisService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * When each job last finished and how many records it handled, for the admin's overview. Kept in
 * Redis, so it is the last run of any instance (or of the retention job run on its own).
 */
@Service
public class JobRunService {

  public enum Job {
    LOCATION_SAVE("location-save"),
    LOCATION_RETENTION("location-retention");

    private final String key;

    Job(String name) {
      this.key = "job-run:" + name;
    }
  }

  public record JobRun(LocalDateTime at, long count) {}

  private final RedisService redisService;

  public JobRunService(RedisService redisService) {
    this.redisService = redisService;
  }

  /** Records a run that handled {@code count} records, after its transaction commits if any */
  public void recorded(Job job, long count) {
    Runnable save =
        () -> redisService.setValue(job.key, LocalDateTime.now(ZoneOffset.UTC) + "|" + count);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              save.run();
            }
          });
    } else {
      save.run();
    }
  }

  public Optional<JobRun> last(Job job) {
    return redisService
        .getValue(job.key)
        .map(
            value -> {
              var parts = value.split("\\|");
              return new JobRun(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
            });
  }
}
