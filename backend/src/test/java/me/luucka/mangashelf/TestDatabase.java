package me.luucka.mangashelf;

import org.springframework.jdbc.core.JdbcTemplate;

/** The integration tests share one database: each class starts from empty tables. */
public final class TestDatabase {

  private TestDatabase() {}

  /** Empties every application table at once, so no test depends on what an earlier one left. */
  public static void clean(JdbcTemplate jdbc) {
    jdbc.update(
        """
        TRUNCATE users, series, editions, publishers, collection_editions, owned_volumes,
                 purchase_lists, purchase_items, spring_session, spring_session_attributes CASCADE
        """);
  }
}
