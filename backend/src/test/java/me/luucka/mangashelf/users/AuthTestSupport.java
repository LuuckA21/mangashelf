package me.luucka.mangashelf.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** SPA CSRF + session-cookie helpers shared by the integration tests. */
public final class AuthTestSupport {

  private AuthTestSupport() {}

  /** Last non-expired cookie with this name among all Set-Cookie headers. */
  public static Optional<Cookie> cookie(MockHttpServletResponse response, String name) {
    Cookie found = null;
    for (String header : response.getHeaders("Set-Cookie")) {
      if (header.startsWith(name + "=")) {
        var value = header.substring(name.length() + 1).split(";", 2)[0];
        found = value.isEmpty() ? null : new Cookie(name, value);
      }
    }
    return Optional.ofNullable(found);
  }

  public static Cookie xsrf(MockMvc mvc) throws Exception {
    return cookie(mvc.perform(get("/api/v1/auth/me")).andReturn().getResponse(), "XSRF-TOKEN")
        .orElseThrow();
  }

  public static ResultActions loginWith(MockMvc mvc, Cookie xsrf, String u, String p)
      throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .cookie(xsrf)
            .header("X-XSRF-TOKEN", xsrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(u, p)));
  }

  public static ResultActions login(MockMvc mvc, String u, String p) throws Exception {
    return loginWith(mvc, xsrf(mvc), u, p);
  }

  /** Logs in and returns the SESSION cookie. */
  public static Cookie session(MockMvc mvc, String username, String password) throws Exception {
    return cookie(login(mvc, username, password).andReturn().getResponse(), "SESSION")
        .orElseThrow();
  }

  /** Sends the request as that session, with a fresh XSRF token and an optional JSON body. */
  public static ResultActions send(
      MockMvc mvc, MockHttpServletRequestBuilder req, Cookie session, String body)
      throws Exception {
    var token = xsrf(mvc);
    req.cookie(session, token).header("X-XSRF-TOKEN", token.getValue());
    if (body != null) {
      req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mvc.perform(req);
  }
}
