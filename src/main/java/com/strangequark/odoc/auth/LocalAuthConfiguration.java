package com.strangequark.odoc.auth;

import java.util.Map;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Local MVP account configuration. A production provider replaces this profile-bound bootstrap. */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@EnableConfigurationProperties(OdocAuthProperties.class)
class LocalAuthConfiguration {
    private static final PasswordEncoder DEVELOPMENT_BASIC_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return rawPassword.toString().equals(encodedPassword);
        }
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        // Local accounts are always Argon2id. Delegation is retained solely so the temporary
        // profile-bound Basic account can use its explicitly marked {noop} development secret
        // while the frontend moves to cookie sessions.
        return new DelegatingPasswordEncoder(
                "argon2",
                Map.of(
                        "argon2", new Argon2PasswordEncoder(16, 32, 1, 19_456, 2),
                        "noop", DEVELOPMENT_BASIC_ENCODER));
    }

    /**
     * The security chain owns these filters. Disable Boot's separate servlet-container
     * registration so a cookie is not authenticated/checked twice for each request.
     */
    @Bean
    FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilterRegistration(
            SessionAuthenticationFilter filter) {
        FilterRegistrationBean<SessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<SessionCsrfFilter> sessionCsrfFilterRegistration(SessionCsrfFilter filter) {
        FilterRegistrationBean<SessionCsrfFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
