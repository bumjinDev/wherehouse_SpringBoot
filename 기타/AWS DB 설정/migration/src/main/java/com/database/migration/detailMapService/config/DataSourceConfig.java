package com.database.migration.detailMapService.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
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
public class DataSourceConfig {

    // =========================================================================
    // JPA PROPERTIES BEANS
    // =========================================================================
    @Primary
    @Bean
    @ConfigurationProperties("app.datasource.source.jpa")
    public JpaProperties sourceJpaProperties() {
        return new JpaProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.destination.jpa")
    public JpaProperties destinationJpaProperties() {
        return new JpaProperties();
    }


    // =========================================================================
    // SOURCE (LOCAL DB) CONFIGURATION
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
    public LocalContainerEntityManagerFactoryBean sourceEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("sourceDataSource") DataSource dataSource,
            @Qualifier("sourceJpaProperties") JpaProperties jpaProperties) {
        return builder
                .dataSource(dataSource)
                .packages("com.database.migration.detailMapService.domain")
                .properties(jpaProperties.getProperties())
                .persistenceUnit("source")
                .build();
    }

    @Primary
    @Bean(name = "sourceTransactionManager")
    public PlatformTransactionManager sourceTransactionManager(@Qualifier("sourceEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(
            basePackages = "com.database.migration.detailMapService.source",
            entityManagerFactoryRef = "sourceEntityManagerFactory",
            transactionManagerRef = "sourceTransactionManager"
    )
    public static class SourceDbConfig {}


    // =========================================================================
    // DESTINATION (REMOTE DB) CONFIGURATION
    // =========================================================================
    @Bean(name = "destinationProperties")
    @ConfigurationProperties("app.datasource.destination")
    public DataSourceProperties destinationDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "destinationDataSource")
    public DataSource destinationDataSource(@Qualifier("destinationProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "destinationEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean destinationEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("destinationDataSource") DataSource dataSource,
            @Qualifier("destinationJpaProperties") JpaProperties jpaProperties) {
        return builder
                .dataSource(dataSource)
                .packages("com.database.migration.detailMapService.domain")
                .properties(jpaProperties.getProperties())
                .persistenceUnit("destination")
                .build();
    }

    @Bean(name = "destinationTransactionManager")
    public PlatformTransactionManager destinationTransactionManager(@Qualifier("destinationEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(
            basePackages = "com.database.migration.detailMapService.destination",
            entityManagerFactoryRef = "destinationEntityManagerFactory",
            transactionManagerRef = "destinationTransactionManager"
    )
    public static class DestinationDbConfig {}
}