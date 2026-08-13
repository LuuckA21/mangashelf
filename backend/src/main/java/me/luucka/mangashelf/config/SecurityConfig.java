package me.luucka.mangashelf.config;

import me.luucka.mangashelf.user.AppUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Strength 12 rather than the default 10: on a self-hosted box the extra
     * ~100ms per login is unnoticeable, while it meaningfully slows down an
     * offline attack on a leaked hash.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposed as a bean so the login endpoint can authenticate credentials
     * itself, instead of going through {@code formLogin}, which speaks
     * redirects and form encoding rather than JSON.
     */
    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService uds,
                                                       PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return provider::authenticate;
    }

    /**
     * Since Spring Security 6 the context is no longer saved implicitly after
     * a programmatic login: without an explicit repository the session would
     * be silently dropped and every request after login would come back 401.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository contextRepository)
            throws Exception {
        http
                // spa() wires the cookie-based repository and the plain token
                // handler together. Without it the token handed out in the
                // XSRF-TOKEN cookie is BREACH-encoded and never matches what
                // the client echoes back, producing 403s that look random.
                .csrf(CsrfConfigurer::spa)
                .securityContext(sc -> sc.securityContextRepository(contextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // The catalogue is shared data: everyone reads it,
                        // only administrators change it. Expressing this by
                        // URL and method keeps the rule in one place, so a
                        // new controller method cannot quietly escape it.
                        // Import is catalogue work, including the search
                        // that precedes it, so the whole prefix is admin-only
                        // regardless of method.
                        .requestMatchers("/api/metadata/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/manga", "/api/manga/**",
                                "/api/series/**", "/api/volumes/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/manga/**", "/api/series/**", "/api/volumes/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/manga/**", "/api/series/**", "/api/volumes/**")
                        .hasRole("ADMIN")
                        // Anything under /api/collection stays personal and
                        // is open to any signed-in user.
                        .anyRequest().authenticated())
                // An API must answer 401 in JSON. The default entry point
                // redirects to a login page, which a fetch() call would
                // follow and then fail to parse as HTML.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authEx) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            // Written by hand rather than through a mapper: the
                            // payload is a fixed literal, so serialising it would
                            // only tie this filter to a JSON library version.
                            response.getWriter().write("{\"error\":\"unauthorized\"}");
                        })
                        // Denials are raised inside the filter chain, before
                        // any controller advice can see them, so this has to
                        // be answered here or the client gets an HTML page
                        // where it expects JSON.
                        .accessDeniedHandler((request, response, deniedEx) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"error\":\"admin_required\"}");
                        }))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }
}
