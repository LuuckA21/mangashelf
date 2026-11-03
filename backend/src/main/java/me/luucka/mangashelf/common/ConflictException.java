package me.luucka.mangashelf.common;

public class ConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ConflictException(String detail) {
    super(detail);
  }
}
