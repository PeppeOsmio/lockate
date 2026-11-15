package com.peppeosmio.lockate.version;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class VersionControllerTest {

  @Test
  void getVersion_answersTheReleaseAndTheServedApis() {
    var controller = new VersionController("0.7.0");

    var version = controller.getVersion();

    assertThat(version.version()).isEqualTo("0.7.0");
    assertThat(version.apis()).isEqualTo(List.of(1));
  }
}
