package com.arknow.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Auth module Spring configuration.
 * <p>
 * Enables {@link AuthProperties} binding and exposes a {@link PasswordEncoder} bean.
 * BCrypt is chosen over other hash algorithms because it is intentionally slow
 * (cost factor 12 = ~0.3s per hash), making offline brute-force attacks impractical.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
