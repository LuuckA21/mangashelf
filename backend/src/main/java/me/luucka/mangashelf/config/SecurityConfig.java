package me.luucka.mangashelf.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.luucka.mangashelf.users.AppUserDetailsService;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {

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
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(
            c -> c.spa().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
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
