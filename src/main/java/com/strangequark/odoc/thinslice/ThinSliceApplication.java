package com.strangequark.odoc.thinslice;

import com.strangequark.odoc.system.SystemInfoController;
import com.strangequark.odoc.system.ThinSliceCommandController;
import com.strangequark.odoc.config.SecurityConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * No-database executable seam used solely by the Phase 0 transport-contract proof.
 *
 * <p>It intentionally scans only the generic HTTP infrastructure and the two sample endpoints.
 * Production/domain controllers, repositories, Flyway, JPA, JDBC, and scheduled maintenance
 * work do not load. The profile is selected explicitly from {@code OdocApplication}; it is not a
 * production runtime mode.
 */
@Profile("thin-slice")
@SpringBootApplication(
        scanBasePackageClasses = {
            ThinSliceApplication.class,
            ThinSliceSecurityConfiguration.class,
            // Scanning this package also provides the shared request-ID, RFC 9457, and
            // OpenAPI infrastructure. SecurityConfiguration itself is profile-gated out.
            SecurityConfiguration.class,
            SystemInfoController.class,
            ThinSliceCommandController.class
        },
        exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
        })
public class ThinSliceApplication {}
