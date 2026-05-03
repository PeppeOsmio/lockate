package com.peppeosmio.lockate.anonymous_group.jobs;

import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.repository.AGLocationRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Profile({"default", "jobs"})
@Slf4j
@Component
public class LocationRetentionJob {

    private final AGLocationConfigurationProperties agLocationConfigurationProperties;
    private final AGLocationRepository repository;

    public LocationRetentionJob(
            AGLocationConfigurationProperties agLocationConfigurationProperties,
            AGLocationRepository repository) {
        this.agLocationConfigurationProperties = agLocationConfigurationProperties;
        this.repository = repository;
            log.info("[RetentionJob] Starting retention job");
    }

    @Scheduled(cron = "${lockate.retention.anonymous-group.location.cron:0 0 0 * * *}")
    @Transactional
    public void cleanupOldLocations() {
        var before = System.nanoTime();
        var cutoff = Instant.now()
                .minus(agLocationConfigurationProperties.getDuration());
        var deleted = repository.deleteOldLocations(cutoff);
        var after = System.nanoTime();
        var durationMs = (after - before) / 1_000_000;
        log.info("[RetentionJob] Deleted {} old location records in {} ms", deleted,
                durationMs);
    }
}
