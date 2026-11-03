package me.luucka.mangashelf.common;

/** Escapes LIKE wildcards so user input is matched literally; pair with {@code escape '\\'}. */
public final class LikeEscape {

  private LikeEscape() {}

  public static String escape(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
