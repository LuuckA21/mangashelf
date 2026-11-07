package me.luucka.mangashelf.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-sends the session cookie on every API call with a valid session, so its Max-Age counts from
 * the last visit: the user stays logged in until the session timeout of inactivity, even after
 * closing the browser. Spring Session alone writes the cookie only when the session is created.
 */
class SessionCookieRefreshFilter extends OncePerRequestFilter {

  private final CookieSerializer cookies;

  SessionCookieRefreshFilter(CookieSerializer cookies) {
    this.cookies = cookies;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var id = request.getRequestedSessionId();
    if (id != null
        && request.isRequestedSessionIdValid()
        && request.getRequestURI().startsWith("/api/")) {
      // Written before the chain; a logout later in this request appends an expiring cookie that
      // wins.
      cookies.writeCookieValue(new CookieValue(request, response, id));
    }
    chain.doFilter(request, response);
  }
}
