package me.luucka.mangashelf.users;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;

/** While the password is temporary, only the endpoints needed to change it are reachable. */
public class MustChangePasswordFilter extends OncePerRequestFilter {

  private static final Set<String> ALLOWED =
      Set.of(
          "GET /api/v1/auth/me",
          "POST /api/v1/auth/login",
          "POST /api/v1/auth/password",
          "POST /api/v1/auth/logout");

  private final HandlerExceptionResolver resolver;

  public MustChangePasswordFilter(HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    // Decoded path, as MVC and Spring Security match it: "/%61pi/..." must not slip past.
    var path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    // Only the API is gated: the SPA shell and assets must load so the user can reach the form.
    if (auth != null
        && path.startsWith("/api/")
        && auth.getPrincipal() instanceof AppUserPrincipal p
        && p.mustChangePassword()
        && !ALLOWED.contains(request.getMethod() + " " + path)) {
      // Reuses ApiExceptionHandler so the 403 is a Problem Details body.
      resolver.resolveException(
          request, response, null, new AccessDeniedException("Password change required"));
      return;
    }
    chain.doFilter(request, response);
  }
}
