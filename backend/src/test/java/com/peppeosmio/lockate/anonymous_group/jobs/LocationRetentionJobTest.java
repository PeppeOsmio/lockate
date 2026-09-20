package com.peppeosmio.lockate.anonymous_group.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.anonymous_group.configuration_properties.AGLocationConfigurationProperties;
import com.peppeosmio.lockate.anonymous_group.repository.AGLocationRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationRetentionJobTest {

  @Mock private AGLocationConfigurationProperties agLocationConfigurationProperties;
  @Mock private AGLocationRepository agLocationRepository;

  private LocationRetentionJob job;

  @BeforeEach
  void setUp() {
    job = new LocationRetentionJob(agLocationConfigurationProperties, agLocationRepository);
  }

  @Test
  void cleanupOldLocations_passesCutoffBasedOnConfiguredRetentionDuration() {
    var retention = Duration.ofDays(30);
    when(agLocationConfigurationProperties.getDuration()).thenReturn(retention);
    when(agLocationRepository.deleteOldLocations(any())).thenReturn(0);
    var before = Instant.now();

    job.cleanupOldLocations();

    var after = Instant.now();
    var captor = ArgumentCaptor.forClass(Instant.class);
    verify(agLocationRepository).deleteOldLocations(captor.capture());
    var expectedCutoffLowerBound = before.minus(retention);
    var expectedCutoffUpperBound = after.minus(retention);
    assertThat(captor.getValue())
        .isBetween(
            expectedCutoffLowerBound.minusSeconds(1), expectedCutoffUpperBound.plusSeconds(1));
  }
}
