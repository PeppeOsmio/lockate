package com.peppeosmio.lockate.anonymous_group.exceptions;

import com.peppeosmio.lockate.common.exceptions.NotFoundException;
import java.util.UUID;

public class AGMemberNotFoundException extends NotFoundException {
  public AGMemberNotFoundException(UUID agMemberId) {
    super(agMemberId.toString());
  }
}
