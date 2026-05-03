package com.peppeosmio.lockate.common.exceptions;

public class NotFoundException extends Exception {
    public NotFoundException(String id) {
        super(id);
    }
}
