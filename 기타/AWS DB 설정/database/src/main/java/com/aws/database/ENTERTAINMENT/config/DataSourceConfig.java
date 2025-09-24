package com.aws.database.ENTERTAINMENT.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

    // =========================================================================
    // SOURCE (ORIGINAL DATA DB) CONFIGURATION
    // =========================================================================
    @Primary
    @Bean(name = "sourceProperties")
    @ConfigurationProperties("app.datasource.source")
    public DataSourceProperties sourceDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "sourceDataSource")
    public DataSource sourceDataSource(@Qualifier("sourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "sourceEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean sourceEntityManagerFactory(EntityManagerFactoryBuilder builder, @Qualifier("sourceDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.WhereHouse.AnalysisData.domain") // Entity 패키지
                .persistenceUnit("source")
                .build();
    }

    @Primary
    @Bean(name = "sourceTransactionManager")
    public PlatformTransactionManager sourceTransactionManager(@Qualifier("sourceEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.WhereHouse.AnalysisData.source", // Source DB용 Repository 패키지
            entityManagerFactoryRef = "sourceEntityManagerFactory",
            transactionManagerRef = "sourceTransactionManager"
    )
    public static class SourceDbConfig {}


// =========================================================================
// DESTINATION (ANALYSIS DB) CONFIGURATION
// =