package me.luucka.mangashelf.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.luucka.mangashelf.users.AppUserDetailsService;
import me.luucka.mangashelf.users.MustChangePasswordFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

  /** Shared with AuthController, which rotates/clears the token on login/logout. */
  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    return CookieCsrfTokenRepository.withHttpOnlyFalse();
  }

  /** Spring Session ignores server.servlet.session.cookie.*; reuse the secure flag from there. */
  @Bean
  CookieSerializer cookieSerializer(
      @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
    var serializer = new DefaultCookieSerializer();
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    serializer.setUseSecureCookie(secure);
    return serializer;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(
      AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    var provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CsrfTokenRepository csrfTokenRepository,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
      throws Exception {
    return http.requestCache(c -> c.disable())
        .csrf(c -> c.spa().csrfTokenRepository(csrfTokenRepository))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .addFilterAfter(
            new MustChangePasswordFilter(exceptionResolver), SecurityContextHolderFilter.class)
        .headers(h ->
            h.contentSecurityPolicy(
                    csp -> csp.policyDirectives("default-src 'self'; img-src 'self' blob:"))
                .frameOptions(f -> f.deny())
                .referrerPolicy(r ->
                    r.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers("/api/v1/auth/login")
                .permitAll()
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .permitAll())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }

  /** Forces the deferred CSRF token to load so the XSRF-TOKEN cookie is sent on every response. */
  static class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws java.io.IOException, jakarta.servlet.ServletException {
      var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (token != null) {
        token.getToken();
      }
      chain.doFilter(request, response);
    }
  }
}
