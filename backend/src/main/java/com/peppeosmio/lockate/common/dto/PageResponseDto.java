package com.peppeosmio.lockate.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponseDto<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponseDto<T> of(Page<?> page, List<T> items) {
    return new PageResponseDto<>(
        items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
}
