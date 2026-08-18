package com.strangequark.odoc;

import static org.assertj.core.api.Assertions.assertThat;

import com.strangequark.odoc.config.OdocRuntimeProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the non-HTTP runtime profiles load their actual application context
 * against PostgreSQL. This keeps worker/parser deployment configuration from
 * drifting behind the API-only test lane.
 */
@Testcontainers(disabledWithoutDocker = true)
class RuntimeProfileContextIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine")
            .withDatabaseName("odoc")
            .withUsername("odoc")
            .withPassword("odoc");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Nested
    @ActiveProfiles({"local", "worker"})
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    class WorkerProfile {
        @org.springframework.beans.factory.annotation.Autowired
        private ApplicationContext context;

        @Test
        void loadsWithoutAnHttpServer() {
            assertThat(context).isNotInstanceOf(ServletWebServerApplicationContext.class);
            assertThat(context.getBean(OdocRuntimeProperties.class).mode())
                    .isEqualTo(OdocRuntimeProperties.Mode.WORKER);
        }
    }

    @Nested
    @ActiveProfiles({"local", "parser"})
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    class ParserProfile {
        @org.springframework.beans.factory.annotation.Autowired
        private ApplicationContext context;

        @Test
        void loadsTheInertParserRoleWithoutAnHttpServer() {
            assertThat(context).isNotInstanceOf(ServletWebServerApplicationContext.class);
            assertThat(context.getBean(OdocRuntimeProperties.class).mode())
                    .isEqualTo(OdocRuntimeProperties.Mode.PARSER);
        }
    }
}
