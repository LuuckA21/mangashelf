package me.luucka.mangashelf.common;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Oversized uploads (413) are already mapped by ResponseEntityExceptionHandler.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  public record FieldError(String field, String message) {}

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  ProblemDetail conflict(ConflictException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail optimisticLock() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "La risorsa è stata modificata da un altro utente");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail integrity(DataIntegrityViolationException e) {
    var message = e.getMostSpecificCause().getMessage();
    if (message != null && message.contains("uq_")) {
      return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Valore già in uso");
    }
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno del server");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail forbidden() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Accesso negato");
  }

  @ExceptionHandler(BadCredentialsException.class)
  ProblemDetail badCredentials() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenziali non valide");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dati non validi");
    List<FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
            .toList();
    problem.setProperty("errors", errors);
    return handleExceptionInternal(ex, problem, headers, status, request);
  }
}
