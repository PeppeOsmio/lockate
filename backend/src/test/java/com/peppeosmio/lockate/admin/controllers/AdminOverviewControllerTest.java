package com.peppeosmio.lockate.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.peppeosmio.lockate.admin.dto.AdminOverviewDto;
import com.peppeosmio.lockate.admin.service.AdminOverviewService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminOverviewControllerTest {

  @Mock private AdminOverviewService service;

  @Test
  void overview_answersTheServicesOverview() {
    var overview =
        new AdminOverviewDto(
            "0.9.0", List.of(1), null, false, null, 0, 0, "", true, true, null, null, null, null);
    when(service.overview()).thenReturn(overview);

    assertThat(new AdminOverviewController(service).overview()).isSameAs(overview);
  }
}
