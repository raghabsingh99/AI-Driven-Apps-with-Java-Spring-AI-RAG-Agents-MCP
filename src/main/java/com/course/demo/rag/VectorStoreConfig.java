package com.course.demo.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Persistent {@link VectorStore} backed by {@link MariaDBVectorStore} - on the separate
 * MariaDB datasource from {@link VectorStoreDataSourceConfig}, so documents survive app
 * restarts (unlike {@code SimpleVectorStore}'s in-memory one). Seeded once with a handful
 * of sample documents about this course's own topics, using the auto-configured
 * {@link EmbeddingModel} to embed them; the seed is skipped on later restarts since the
 * table already has rows, to avoid re-inserting duplicates every time the app starts.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(@Qualifier("vectorStoreJdbcTemplate") JdbcTemplate jdbcTemplate,
                                    EmbeddingModel embeddingModel) throws Exception {
        MariaDBVectorStore vectorStore = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .dimensions(embeddingModel.dimensions())
                .build();
        // Schema creation normally runs via the InitializingBean callback once Spring finishes
        // constructing this bean, which is too late for the table-existence check below - run it
        // now so the table is guaranteed to exist before we query/seed it.
        vectorStore.afterPropertiesSet();

        Integer existingDocumentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + MariaDBVectorStore.DEFAULT_TABLE_NAME, Integer.class);
        if (existingDocumentCount == null || existingDocumentCount == 0) {
            vectorStore.add(sampleDocuments());
        }

        return vectorStore;
    }

    // Backs VectorStoreController's /ingest endpoint - the reference .txt files living in
    // src/main/resources/rag describe the same course topics as the hardcoded sampleDocuments()
    // below, but are ingested into the VectorStore on demand instead of being seeded automatically.
    @Bean
    public Resource[] ragDocumentResources(@Value("classpath:/rag/*.txt") Resource[] resources) {
        return resources;
    }

    private List<Document> sampleDocuments() {
        return List.of(
                new Document("Spring AI's ChatClient provides a fluent API for interacting with chat "
                        + "models, supporting prompts, advisors, and structured output.",
                        Map.of("topic", "chat-client")),
                new Document("Chat memory lets a conversation retain prior messages across calls, backed "
                        + "by an in-memory, JDBC, or other ChatMemoryRepository implementation.",
                        Map.of("topic", "chat-memory")),
                new Document("Retrieval-Augmented Generation (RAG) grounds model responses in external "
                        + "documents by retrieving relevant chunks from a vector store and injecting "
                        + "them into the prompt.",
                        Map.of("topic", "rag")),
                new Document("Embeddings turn text into numeric vectors that capture semantic meaning, "
                        + "enabling similarity search via metrics like cosine similarity.",
                        Map.of("topic", "embeddings")),
                new Document("The Model Context Protocol (MCP) standardizes how applications expose "
                        + "tools, resources, and prompts to AI models and agents.",
                        Map.of("topic", "mcp")));
    }
}
