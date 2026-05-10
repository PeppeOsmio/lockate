package com.peppeosmio.lockate.anonymous_group.jobs;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("job-location-retention")
@Component
public class LocationRetentionJobRunner implements ApplicationRunner {

    private final LocationRetentionJob locationRetentionJob;

    public LocationRetentionJobRunner(LocationRetentionJob locationRetentionJob) {
        this.locationRetentionJob = locationRetentionJob;
    }

    @Override
    public void run(ApplicationArguments args) {
        locationRetentionJob.cleanupOldLocations();
    }
}
