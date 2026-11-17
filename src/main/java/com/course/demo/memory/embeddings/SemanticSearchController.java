package com.course.demo.memory.embeddings;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Computes the cosine similarity between the embeddings of two pieces of text,
 * using the auto-configured {@link EmbeddingModel} - the same similarity metric
 * vector stores use under the hood for semantic search / RAG retrieval.
 */
@RestController
@RequestMapping(path = "/api/semantic-search", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SemanticSearchController {

    private final EmbeddingModel embeddingModel;

    @GetMapping("/cosine-similarity")
    public CosineSimilarityResponse cosineSimilarity(
            @RequestParam(defaultValue = "Spring AI makes it easy to build AI applications.") String text1,
            @RequestParam(defaultValue = "Spring AI simplifies building applications powered by AI.") String text2) {

        float[] embedding1 = embeddingModel.embed(text1);
        float[] embedding2 = embeddingModel.embed(text2);

        return CosineSimilarityResponse.builder()
                .text1(text1)
                .text2(text2)
                .cosineSimilarity(cosineSimilarity(embedding1, embedding2))
                .build();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
