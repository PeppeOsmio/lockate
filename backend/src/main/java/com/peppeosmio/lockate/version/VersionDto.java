package com.peppeosmio.lockate.version;

import java.util.List;

public record VersionDto(String version, List<Integer> apis) {}
