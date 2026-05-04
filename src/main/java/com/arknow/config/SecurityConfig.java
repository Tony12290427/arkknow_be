package com.arknow.config;

import com.arknow.auth.token.JwtService;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for a stateless JWT-based REST API.
 * <p>
 * Design decisions:
 * <ul>
 *   <li>CSRF is disabled — browser-based sessions are not used; all auth is via Bearer tokens</li>
 *   <li>Session management is STATELESS — the server stores no HTTP session state</li>
 *   <li>Auth endpoints are public; profile and content endpoints require authentication</li>
 *   <li>JWT decoding uses RS256 public key verification via Nimbus JOSE</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/send-code", "/api/v1/auth/register",
                    "/api/v1/auth/login", "/api/v1/auth/token/refresh",
                    "/api/v1/auth/logout").permitAll()
                .requestMatchers("/api/v1/auth/me", "/api/v1/profile/**",
                    "/api/v1/knowposts/drafts", "/api/v1/knowposts/*/publish",
                    "/api/v1/knowposts/*/content/confirm", "/api/v1/knowposts/mine",
                    "/api/v1/storage/presign").authenticated()
                .requestMatchers("/api/v1/knowposts/feed", "/api/v1/knowposts/detail/*").permitAll()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    /**
     * Custom JWT decoder that delegates to {@link JwtService} for RS256 verification
     * and converts Nimbus {@link SignedJWT} to Spring Security's {@link Jwt}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            SignedJWT signed = jwtService.decode(token);
            try {
                return new Jwt(
                    token,
                    signed.getJWTClaimsSet().getIssueTime().toInstant(),
                    signed.getJWTClaimsSet().getExpirationTime().toInstant(),
                    signed.getHeader().toJSONObject(),
                    signed.getJWTClaimsSet().toJSONObject()
                );
            } catch (java.text.ParseException e) {
                throw new IllegalArgumentException("Failed to parse JWT claims", e);
            }
        };
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
