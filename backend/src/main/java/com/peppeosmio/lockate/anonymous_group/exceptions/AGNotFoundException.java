package com.peppeosmio.lockate.anonymous_group.exceptions;

import com.peppeosmio.lockate.common.exceptions.NotFoundException;

import java.util.UUID;

public class AGNotFoundException extends NotFoundException {
    public AGNotFoundException(UUID anonymousGroupId) {
        super(anonymousGroupId.toString());
    }
}
