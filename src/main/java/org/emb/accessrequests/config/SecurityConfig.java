package org.emb.accessrequests.config;

import org.emb.accessrequests.auth.security.JwtUserAuthenticationConverter;
import org.emb.accessrequests.auth.service.DbUserDetailsService;
import org.emb.accessrequests.error.ErrorCodes;
import org.emb.accessrequests.error.ErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless JWT authentication for a JSON API: {@code POST /api/auth/login}
 * returns an access token, and every other call sends it as
 * {@code Authorization: Bearer <token>}. No session and no cookies.
 *
 * <p><b>CSRF.</b> Spring's CSRF token is turned off: browsers never attach the
 * {@code Authorization} header on their own, so a cross-site page can't make an
 * authenticated request. CORS stays disabled; the frontend is served from the same
 * origin (through the Vite proxy in development).
 *
 * <p>Roles are checked here per route. Which requests a reviewer can see and decide
 * is checked in {@code RequestService}. 401 and 403 use the contract's JSON body:
 * no redirects, no login page and no Basic-auth popup.
 */
@Configuration
public class SecurityConfig {

    /** Login and logout ignore the bearer token, so a stale token never blocks them with a 401. */
    private static final RequestMatcher TOKEN_OPTIONAL = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout"));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtUserAuthenticationConverter jwtConverter,
            AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)        // AuthController handles logout
                .requestCache(AbstractHttpConfigurer::disable)  // never redirect after login
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/requests/mine").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/requests").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/approvals").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/requests/*/decision").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/summary").authenticated()
                        .anyRequest().authenticated());
        return http.build();
    }

    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        return request -> TOKEN_OPTIONAL.matches(request) ? null : header.resolve(request);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(DbUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ErrorResponseWriter writer) {
        return (request, response, ex) -> writer.write(response, 401,
                ErrorCodes.UNAUTHENTICATED, ErrorCodes.UNAUTHENTICATED_MESSAGE);
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ErrorResponseWriter writer) {
        return (request, response, ex) -> writer.write(response, 403,
                ErrorCodes.FORBIDDEN, ErrorCodes.FORBIDDEN_MESSAGE);
    }
}
