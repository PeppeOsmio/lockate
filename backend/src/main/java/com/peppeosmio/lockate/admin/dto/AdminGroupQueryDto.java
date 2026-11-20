package com.peppeosmio.lockate.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * A page of groups. {@code q} is the start of a group's or one of its members' id; {@code
 * inactiveDays} keeps the groups older than that with no position saved since; {@code noAdmin} the
 * groups where nobody is an admin.
 */
public record AdminGroupQueryDto(
    @Min(0) Integer page,
    @Min(1) @Max(100) Integer size,
    @Pattern(regexp = "[0-9a-fA-F-]{1,36}") String q,
    @Pattern(regexp = "created|members|activity") String sort,
    @Min(1) @Max(3650) Integer inactiveDays,
    Boolean noAdmin) {

  public AdminGroupQueryDto {
    page = page == null ? 0 : page;
    size = size == null ? 20 : size;
    sort = sort == null ? "created" : sort;
  }
}
