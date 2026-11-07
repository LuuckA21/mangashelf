package me.luucka.mangashelf.common;

import java.util.List;
import me.luucka.mangashelf.catalog.InvalidImageException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  public record FieldError(String field, String message) {}

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(FieldValidationException.class)
  ProblemDetail invalidField(FieldValidationException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dati non validi");
    problem.setProperty("errors", List.of(new FieldError(e.field(), e.getMessage())));
    return problem;
  }

  @ExceptionHandler(InvalidImageException.class)
  ProblemDetail invalidImage(InvalidImageException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
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
    var constraint = constraintName(e);
    if (constraint.startsWith("uq_")) {
      return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Valore già in uso");
    }
    if (constraint.startsWith("fk_")) {
      return ProblemDetail.forStatusAndDetail(
          HttpStatus.CONFLICT, "Il dato è stato modificato da un'altra operazione, riprova");
    }
    log.error("Unexpected data integrity violation", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno del server");
  }

  private static String constraintName(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
        return cve.getConstraintName();
      }
    }
    return "";
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
