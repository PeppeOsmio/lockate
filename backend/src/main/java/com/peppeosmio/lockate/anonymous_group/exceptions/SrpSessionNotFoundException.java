package com.peppeosmio.lockate.anonymous_group.exceptions;

public class SrpSessionNotFoundException extends RuntimeException {
    public SrpSessionNotFoundException(String sessionId) {
        super(sessionId);
    }
}
