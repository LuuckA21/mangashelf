package me.luucka.mangashelf.catalog;

public class InvalidImageException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InvalidImageException(String detail) {
    super(detail);
  }
}
