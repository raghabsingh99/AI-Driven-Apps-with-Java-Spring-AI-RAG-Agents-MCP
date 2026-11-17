package com.course.demo.rag;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Two independent datasources on two different database engines (see application.properties):
 * the MySQL one backing chat memory (spring.datasource.*), kept primary so the rest of
 * the app (JdbcChatMemoryRepository, etc.) is unaffected, and a separate MariaDB one
 * (app.vectorstore.datasource.*) dedicated to the persistent
 * {@link org.springframework.ai.vectorstore.VectorStore} in {@link VectorStoreConfig} - MariaDB
 * specifically because it has native VECTOR column / distance-function support that MySQL lacks.
 * Defining a second DataSource bean requires both to be explicit - Spring Boot's own
 * auto-configured DataSource/JdbcTemplate backs off entirely once any DataSource bean exists.
 * The DataSourceProperties parameters below are explicitly {@code @Qualifier}'d - without
 * it, Spring resolves an ambiguous-by-type parameter via {@code @Primary} *before* falling
 * back to parameter-name matching, so vectorStoreDataSource(...) would otherwise silently
 * receive chatMemoryDataSourceProperties instead of its own.
 */
@Configuration
public class VectorStoreDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties chatMemoryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    public DataSource chatMemoryDataSource(
            @Qualifier("chatMemoryDataSourceProperties") DataSourceProperties chatMemoryDataSourceProperties) {
        return chatMemoryDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("chatMemoryDataSource") DataSource chatMemoryDataSource) {
        return new JdbcTemplate(chatMemoryDataSource);
    }

    @Bean
    @ConfigurationProperties("app.vectorstore.datasource")
    public DataSourceProperties vectorStoreDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource vectorStoreDataSource(
            @Qualifier("vectorStoreDataSourceProperties") DataSourceProperties vectorStoreDataSourceProperties) {
        return vectorStoreDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate vectorStoreJdbcTemplate(@Qualifier("vectorStoreDataSource") DataSource vectorStoreDataSource) {
        return new JdbcTemplate(vectorStoreDataSource);
    }
}
