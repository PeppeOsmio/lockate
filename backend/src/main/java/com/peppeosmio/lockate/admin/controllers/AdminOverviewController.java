package com.peppeosmio.lockate.admin.controllers;

import com.peppeosmio.lockate.admin.dto.AdminOverviewDto;
import com.peppeosmio.lockate.admin.security.SecuredAdmin;
import com.peppeosmio.lockate.admin.service.AdminOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SecuredAdmin
@RestController
public class AdminOverviewController {

  private final AdminOverviewService service;

  public AdminOverviewController(AdminOverviewService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/admin/overview")
  public AdminOverviewDto overview() {
    return service.overview();
  }
}
