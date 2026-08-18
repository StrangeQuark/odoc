package com.strangequark.odoc.encryption;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** Enables the deliberately local-only KEK adapter when an operator supplies a development key. */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "test"})
@EnableConfigurationProperties(OdocManagedEncryptionProperties.class)
class LocalManagedEncryptionConfiguration {

    @Bean
    @ConditionalOnProperty(name = "odoc.encryption.managed.enabled", havingValue = "true")
    KeyWrappingProvider localKeyWrappingProvider(OdocManagedEncryptionProperties properties) {
        if (properties.wrappingKeyBase64() == null || properties.wrappingKeyBase64().isBlank()) {
            throw new IllegalStateException("Managed encryption is enabled but no wrapping key was supplied.");
        }
        return new LocalAesKeyWrappingProvider(properties.wrappingKeyBase64());
    }

    @Bean
    @ConditionalOnProperty(name = "odoc.encryption.managed.enabled", havingValue = "true")
    DataEncryptionKeyProvider persistentDataEncryptionKeyProvider(
            ManagedDataKeyRepository repository, KeyWrappingProvider wrappingProvider, JdbcTemplate jdbcTemplate) {
        return new PersistentDataEncryptionKeyProvider(repository, wrappingProvider, jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "odoc.encryption.managed.enabled", havingValue = "true")
    ManagedRecordEncryption managedRecordEncryption(DataEncryptionKeyProvider keys) {
        return new ManagedRecordEncryption(keys);
    }
}
