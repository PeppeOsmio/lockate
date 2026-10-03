package com.peppeosmio.lockate.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PageQueryDtoTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void constructor_nullValues_appliesDefaults() {
    var query = new PageQueryDto(null, null);

    assertThat(query.page()).isZero();
    assertThat(query.size()).isEqualTo(20);
  }

  @Test
  void constructor_explicitValues_preservesThem() {
    var query = new PageQueryDto(3, 50);

    assertThat(query.page()).isEqualTo(3);
    assertThat(query.size()).isEqualTo(50);
  }

  @Test
  void validate_withinBounds_hasNoViolations() {
    assertThat(validator.validate(new PageQueryDto(0, 1))).isEmpty();
    assertThat(validator.validate(new PageQueryDto(0, 100))).isEmpty();
  }

  @Test
  void validate_negativePage_hasViolation() {
    assertThat(validator.validate(new PageQueryDto(-1, 20))).hasSize(1);
  }

  @Test
  void validate_sizeOutOfBounds_hasViolation() {
    assertThat(validator.validate(new PageQueryDto(0, 0))).hasSize(1);
    assertThat(validator.validate(new PageQueryDto(0, 101))).hasSize(1);
  }
}
