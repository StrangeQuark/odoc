package com.strangequark.odoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OdocRuntimePropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RuntimePropertiesConfiguration.class);

    @Test
    void bindsTheExplicitApiRuntimeMode() {
        contextRunner.withPropertyValues("odoc.runtime.mode=API").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OdocRuntimeProperties.class).mode())
                    .isEqualTo(OdocRuntimeProperties.Mode.API);
        });
    }

    @Test
    void rejectsAnUnknownRuntimeModeBeforeApplicationStartup() {
        contextRunner.withPropertyValues("odoc.runtime.mode=untrusted").run(context -> {
            assertThat(context).hasFailed();
            Throwable failure = context.getStartupFailure();
            while (failure.getCause() != null) {
                failure = failure.getCause();
            }
            assertThat(failure).hasMessageContaining("No enum constant");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OdocRuntimeProperties.class)
    static class RuntimePropertiesConfiguration {}
}
