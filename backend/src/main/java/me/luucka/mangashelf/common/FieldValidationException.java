package me.luucka.mangashelf.common;

/** Request-level validation that Bean Validation cannot express; mapped to 400 with errors[]. */
public class FieldValidationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String field;

  public FieldValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
