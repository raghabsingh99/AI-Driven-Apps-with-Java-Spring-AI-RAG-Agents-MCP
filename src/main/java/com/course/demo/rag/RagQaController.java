package com.course.demo.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retrieval-Augmented Generation Q&A: answers a question grounded in the {@link VectorStoreConfig}'s
 * VectorStore instead of the model's own unverifiable knowledge, and returns the specific sources
 * the answer was built from alongside it, so the caller can verify the claims.
 */
@RestController
@RequestMapping(path = "/api/rag-qa", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RagQaController {

    private static final int EXCERPT_MAX_LENGTH = 300;
    private static final String NO_CONTEXT_ANSWER =
            "I don't have enough information in the knowledge base to answer that.";

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;

    @Value("classpath:/prompts/rag-qa-system.st")
    private Resource systemPromptTemplate;

    @GetMapping("/ask")
    public RagAnswerResponse ask(@RequestParam(defaultValue = "How does RAG work?") String question,
                                  @RequestParam(defaultValue = "3") int topK,
                                  @RequestParam(defaultValue = "0.5") double similarityThreshold) {
        List<Document> retrieved = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build());

        if (retrieved.isEmpty()) {
            return RagAnswerResponse.builder()
                    .question(question)
                    .answer(NO_CONTEXT_ANSWER)
                    .citations(List.of())
                    .build();
        }

        String systemMessage = new PromptTemplate(systemPromptTemplate)
                .render(Map.of("context", buildNumberedContext(retrieved)));

        String answer = chatClientBuilder.build()
                .prompt()
                .system(systemMessage)
                .user(question)
                .call()
                .content();

        return RagAnswerResponse.builder()
                .question(question)
                .answer(answer)
                .citations(toCitations(retrieved, answer))
                .build();
    }

    // Numbers each retrieved document so the model's inline citations ([1], [2], ...) line up
    // positionally with the "citations" list returned alongside the answer.
    private String buildNumberedContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            String title = document.getMetadata().getOrDefault("title", "Untitled").toString();
            context.append("[%d] %s\n%s\n\n".formatted(i + 1, title, document.getText()));
        }
        return context.toString();
    }

    // Position in this list (1-based) is exactly the citation number the model was given in
    // buildNumberedContext - "cited" reports whether the model's answer actually contains that
    // number's bracket marker, so callers can tell retrieved-but-unused sources from ones the
    // answer genuinely relies on.
    private List<Citation> toCitations(List<Document> documents, String answer) {
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            citations.add(toCitation(documents.get(i), i + 1, answer));
        }
        return citations;
    }

    private Citation toCitation(Document document, int number, String answer) {
        Map<String, Object> metadata = document.getMetadata();
        String title = metadata.getOrDefault("title", "Untitled").toString();
        String source = metadata.containsKey("source")
                ? metadata.get("source").toString()
                : metadata.getOrDefault("topic", "unknown").toString();
        boolean cited = answer.contains("[" + number + "]");

        return new Citation(number, title, source, document.getScore(), excerpt(document.getText()), cited);
    }

    private String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= EXCERPT_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, EXCERPT_MAX_LENGTH).strip() + "...";
    }
}
