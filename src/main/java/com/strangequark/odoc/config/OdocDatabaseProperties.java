package com.strangequark.odoc.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Safe, operator-configurable PostgreSQL connection limits.
 *
 * <p>The application generates the session SQL itself instead of interpolating arbitrary SQL from
 * environment variables. That keeps the search path, UTC handling, and timeout policy explicit.
 */
@Validated
@ConfigurationProperties("odoc.database")
public record OdocDatabaseProperties(
        int maximumPoolSize,
        int minimumIdle,
        Duration connectionTimeout,
        Duration validationTimeout,
        Duration maxLifetime,
        Duration statementTimeout,
        Duration lockTimeout,
        Duration idleInTransactionTimeout) {

    public OdocDatabaseProperties {
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be at least one");
        }
        if (minimumIdle < 0 || minimumIdle > maximumPoolSize) {
            throw new IllegalArgumentException("minimumIdle must be between zero and maximumPoolSize");
        }
        requirePositive("connectionTimeout", connectionTimeout);
        requirePositive("validationTimeout", validationTimeout);
        requirePositive("maxLifetime", maxLifetime);
        requirePositive("statementTimeout", statementTimeout);
        requirePositive("lockTimeout", lockTimeout);
        requirePositive("idleInTransactionTimeout", idleInTransactionTimeout);
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
