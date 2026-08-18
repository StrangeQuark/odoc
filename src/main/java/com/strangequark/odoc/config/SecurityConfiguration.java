package com.strangequark.odoc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.ObjectProvider;
import com.strangequark.odoc.auth.SessionAuthenticationFilter;
import com.strangequark.odoc.auth.SessionCsrfFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Profile("!thin-slice")
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<SessionAuthenticationFilter> sessionFilter,
            ObjectProvider<SessionCsrfFilter> csrfFilter)
            throws Exception {
        var security = http
                .csrf(csrf -> csrf.disable()) // Cookie sessions use the explicit filter below; Basic stays stateless.
                // The temporary Basic-auth development profile is deliberately
                // stateless. P1 replaces it with the reviewed secure-session
                // boundary instead of letting Spring create incidental sessions.
                .requestCache(requestCache -> requestCache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**", "/api/v1/system/info", "/v3/api-docs/**", "/api/v1/auth/register",
                                "/api/v1/auth/login", "/api/v1/auth/email-verification",
                                "/api/v1/auth/password-recovery/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "You do not have permission for this resource.")))
                .httpBasic(Customizer.withDefaults())
                ;
        SessionAuthenticationFilter filter = sessionFilter.getIfAvailable();
        if (filter != null) {
            security.addFilterBefore(filter, org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class);
        }
        SessionCsrfFilter csrf = csrfFilter.getIfAvailable();
        if (csrf != null) {
            security.addFilterAfter(csrf, SessionAuthenticationFilter.class);
        }
        return security.build();
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detail)
            throws IOException {
        Object requestId = request.getAttribute(RequestIdFilter.HEADER);
        String requestIdProperty = requestId instanceof String value
                ? ",\"requestId\":\"" + json(value) + "\""
                : "";
        String responseBody = "{\"type\":\"https://odoc.local/problems/" + status.value()
                + "\",\"title\":\"" + json(status.getReasonPhrase()) + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + json(detail) + "\",\"instance\":\"" + json(request.getRequestURI())
                + "\"" + requestIdProperty + "}";

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(responseBody);
    }

    private String json(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
