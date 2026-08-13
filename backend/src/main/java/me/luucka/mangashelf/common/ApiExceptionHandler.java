package me.luucka.mangashelf.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(Map.of("error", "validation_failed", "fields", fields));
    }

    /**
     * Malformed or unbindable JSON. Spring's default response for this is a
     * bare 400 with no clue as to what failed, which is indistinguishable
     * from a validation error at the call site.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        return ResponseEntity.badRequest()
                .body(Map.of("error", "malformed_request",
                        "detail", detail == null ? "" : detail));
    }

    /**
     * Both a wrong password and an unknown username answer the same way, so
     * the endpoint cannot be used to enumerate which accounts exist.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "invalid_credentials"));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "account_disabled"));
    }
}
