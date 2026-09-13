package me.luucka.mangashelf.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.luucka.mangashelf.user.dto.UserResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Instant;

@Component
public class LoginSession {
    static final String PENDING = LoginSession.class.getName() + ".pending";
    private final SecurityContextRepository contexts;

    public LoginSession(SecurityContextRepository contexts) { this.contexts = contexts; }

    /** Server-side, password-free, unprivileged challenge bound to this browser session. */
    static final class Pending implements Serializable {
        final UserPrincipal principal;
        final Instant expires = Instant.now().plusSeconds(300);
        final int fullSessionTimeout;
        int attempts;
        boolean consumed;
        Pending(AppUser user, int fullSessionTimeout) {
            principal = UserPrincipal.from(user).withoutCredentials();
            this.fullSessionTimeout = fullSessionTimeout;
        }
    }

    public void challenge(AppUser user, HttpServletRequest request) {
        clear(request);
        var session = request.getSession(true);
        int fullTimeout = session.getMaxInactiveInterval();
        session.setMaxInactiveInterval(300);
        session.setAttribute(PENDING, new Pending(user, fullTimeout));
    }

    public UserResponse authenticate(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) == null) request.getSession(true);
        else request.changeSessionId();
        var session = request.getSession(false);
        if (session.getAttribute(PENDING) instanceof Pending pending) {
            session.setMaxInactiveInterval(pending.fullSessionTimeout);
        }
        session.removeAttribute(PENDING);
        var principal = UserPrincipal.from(user).withoutCredentials();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return UserResponse.from(user);
    }

    public static void clear(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            if (session.getAttribute(PENDING) instanceof Pending pending) {
                synchronized (pending) { pending.consumed = true; }
            }
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
