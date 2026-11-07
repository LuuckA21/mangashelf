package me.luucka.mangashelf.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  private static DataIntegrityViolationException violation(String constraint, String message) {
    return new DataIntegrityViolationException(
        "x",
        new ConstraintViolationException(
            message, new SQLException(message), "insert", ConstraintKind.UNIQUE, constraint));
  }

  @Test
  void uniqueConstraintIs409() {
    assertThat(handler.integrity(violation("uq_users_username", "dup")).getStatus()).isEqualTo(409);
  }

  @Test
  void otherViolationIs500EvenIfMessageMentionsUq() {
    assertThat(
            handler
                .integrity(violation("ck_users_role", "Failing row contains (uq_x)"))
                .getStatus())
        .isEqualTo(500);
  }

  @Test
  void foreignKeyViolationIs409() {
    assertThat(handler.integrity(violation("fk_editions_publisher_id", "missing")).getStatus())
        .isEqualTo(409);
  }
}
