package com.teamcrocodile.tripwire.exception;

/** The caller is logged in but is not allowed to do this. Mapped to HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
