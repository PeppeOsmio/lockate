package com.peppeosmio.lockate.anonymous_group.exceptions;

import com.peppeosmio.lockate.common.exceptions.NotFoundException;
import java.util.UUID;

public class AGCircleNotFoundException extends NotFoundException {
  public AGCircleNotFoundException(UUID circleId) {
    super(circleId.toString());
  }
}
