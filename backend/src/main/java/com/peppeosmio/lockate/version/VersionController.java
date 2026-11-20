package com.peppeosmio.lockate.version;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The backend's version and the majors of the Lockate API it serves (docs/versioning.md). Its path
 * has no API version, so an app of any major can ask whether this server speaks its API.
 */
@RestController
public class VersionController {
  public static final List<Integer> SERVED_APIS = List.of(1);

  private final String version;

  public VersionController(@Value("${lockate.version}") String version) {
    this.version = version;
  }

  @GetMapping("/api/version")
  public VersionDto getVersion() {
    return new VersionDto(version, SERVED_APIS);
  }
}
