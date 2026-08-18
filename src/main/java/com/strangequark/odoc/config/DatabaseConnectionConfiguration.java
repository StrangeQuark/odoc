package com.strangequark.odoc.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Configures the application runtime datasource with bounded PostgreSQL sessions. */
@Configuration(proxyBeanMethods = false)
@Profile("!thin-slice")
@EnableConfigurationProperties(OdocDatabaseProperties.class)
class DatabaseConnectionConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    DataSource applicationDataSource(DataSourceProperties source, OdocDatabaseProperties database) {
        HikariDataSource dataSource = source.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setMaximumPoolSize(database.maximumPoolSize());
        dataSource.setMinimumIdle(database.minimumIdle());
        dataSource.setConnectionTimeout(database.connectionTimeout().toMillis());
        dataSource.setValidationTimeout(database.validationTimeout().toMillis());
        dataSource.setMaxLifetime(database.maxLifetime().toMillis());
        dataSource.setConnectionInitSql(sessionInitializationSql(database));
        return dataSource;
    }

    private static String sessionInitializationSql(OdocDatabaseProperties database) {
        return "SET search_path TO public; "
                + "SET TIME ZONE 'UTC'; "
                + "SET statement_timeout TO "
                + database.statementTimeout().toMillis()
                + "; SET lock_timeout TO "
                + database.lockTimeout().toMillis()
                + "; SET idle_in_transaction_session_timeout TO "
                + database.idleInTransactionTimeout().toMillis();
    }
}
