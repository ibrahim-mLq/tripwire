package com.teamcrocodile.tripwire.exception;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Turns service-layer exceptions into consistent JSON error responses,
 * so rule violations come back as 4xx instead of a generic 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Used for case-lifecycle violations, e.g. deciding a case that is already finalised.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, AdminAccessDeniedException.class})
    public ResponseEntity<Map<String, String>> forbidden(RuntimeException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<Map<String, String>> notFound(EmptyResultDataAccessException ex) {
        return error(HttpStatus.NOT_FOUND, "Not found");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        String text = message != null ? message : status.getReasonPhrase();
        // Both keys are sent because the front-end pages read one or the other.
        return ResponseEntity.status(status).body(Map.of("error", text, "message", text));
    }
}
