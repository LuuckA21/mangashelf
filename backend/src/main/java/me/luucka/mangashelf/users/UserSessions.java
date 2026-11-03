package me.luucka.mangashelf.users;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

@Component
public class UserSessions {

  private final FindByIndexNameSessionRepository<?> sessions;

  public UserSessions(FindByIndexNameSessionRepository<?> sessions) {
    this.sessions = sessions;
  }

  /** The session index is keyed by the stored username, i.e. the authenticated principal name. */
  public void closeAll(String username) {
    sessions.findByPrincipalName(username).keySet().forEach(sessions::deleteById);
  }
}
