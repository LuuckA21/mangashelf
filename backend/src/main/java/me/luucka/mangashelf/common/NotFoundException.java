package me.luucka.mangashelf.common;

public class NotFoundException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public NotFoundException(String detail) {
    super(detail);
  }
}
