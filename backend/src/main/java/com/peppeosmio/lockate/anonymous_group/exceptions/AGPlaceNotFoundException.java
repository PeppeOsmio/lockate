package com.peppeosmio.lockate.anonymous_group.exceptions;

import com.peppeosmio.lockate.common.exceptions.NotFoundException;
import java.util.UUID;

public class AGPlaceNotFoundException extends NotFoundException {
  public AGPlaceNotFoundException(UUID placeId) {
    super(placeId.toString());
  }
}
