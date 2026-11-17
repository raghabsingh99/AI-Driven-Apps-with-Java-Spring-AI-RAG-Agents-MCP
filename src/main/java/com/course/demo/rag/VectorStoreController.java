package com.course.demo.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Verifies the {@link VectorStoreConfig}'s seeded {@link VectorStore} by running a
 * similarity search against it and returning the matching documents.
 */
@RestController
@RequestMapping(path = "/api/vector-store", produces = MediaType.TEXT_PLAIN_VALUE)
@RequiredArgsConstructor
public class VectorStoreController {

    private static final int EXCERPT_MAX_LENGTH = 200;

    private final VectorStore vectorStore;
    private final Resource[] ragDocumentResources;

    @GetMapping(path = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SearchResult> search(@RequestParam(defaultValue = "How does RAG work?") String query,
                                      @RequestParam(defaultValue = "3") int topK) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());

        return toSearchResults(results);
    }

    @GetMapping(path = "/search/by-topic", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SearchResult> searchByTopic(@RequestParam(defaultValue = "How does RAG work?") String query,
                                             @RequestParam String topic,
                                             @RequestParam(defaultValue = "3") int topK) {
        Filter.Expression topicFilter = new FilterExpressionBuilder().eq("topic", topic).build();

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).filterExpression(topicFilter).build());

        return toSearchResults(results);
    }

    // Same similarity search as /search, but shaped for display: title/source come from
    // document metadata (set by /ingest; falls back to "topic" or "Untitled"/"unknown" for
    // documents added via /documents or seeded without that metadata), and the full text is
    // trimmed down to a short excerpt instead of being returned in full. similarityThreshold
    // drops weak matches that would otherwise still fill out topK results regardless of how
    // unrelated they are to the query - raise it for stricter relevance, lower/0 to disable.
    @GetMapping(path = "/search/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DocumentSearchResult> searchPreview(@RequestParam(defaultValue = "How does RAG work?") String query,
                                                      @RequestParam(defaultValue = "3") int topK,
                                                      @RequestParam(defaultValue = "0.5") double similarityThreshold) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build());

        return results.stream()
                .map(this::toDocumentSearchResult)
                .collect(Collectors.toList());
    }

    private DocumentSearchResult toDocumentSearchResult(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String title = metadata.getOrDefault("title", "Untitled").toString();
        String source = metadata.containsKey("source")
                ? metadata.get("source").toString()
                : metadata.getOrDefault("topic", "unknown").toString();

        return new DocumentSearchResult(title, source, document.getScore(), excerpt(document.getText()));
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

    private List<SearchResult> toSearchResults(List<Document> documents) {
        return documents.stream()
                .map(document -> SearchResult.builder()
                        .id(document.getId())
                        .text(document.getText())
                        .similarity(document.getScore())
                        .build())
                .collect(Collectors.toList());
    }

    @PostMapping(path = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentResponse addDocument(@RequestBody AddDocumentRequest request) {
        Map<String, Object> metadata = StringUtils.hasText(request.getTopic())
                ? Map.of("topic", request.getTopic())
                : Map.of();
        Document document = new Document(request.getText(), metadata);
        vectorStore.add(List.of(document));

        return DocumentResponse.builder()
                .id(document.getId())
                .text(document.getText())
                .topic(request.getTopic())
                .build();
    }

    // Ingests every reference .txt file under src/main/resources/rag (see VectorStoreConfig's
    // ragDocumentResources bean) into the VectorStore, one Document per file.
    @PostMapping(path = "/ingest", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<IngestedDocumentResponse> ingest() throws IOException {
        List<Document> documents = new ArrayList<>();
        List<IngestedDocumentResponse> responses = new ArrayList<>();

        for (Resource resource : ragDocumentResources) {
            ParsedDocument parsed = extractTitleAndContent(resource);
            String topic = topicFromFilename(resource.getFilename());

            // Makes re-ingestion idempotent: drop any rows already stored under this topic
            // (from a prior /ingest call, or an overlapping seeded sample document) before
            // adding the fresh one, instead of piling up duplicates on every call.
            vectorStore.delete(new FilterExpressionBuilder().eq("topic", topic).build());

            Document document = new Document(parsed.content(),
                    Map.of("title", parsed.title(), "topic", topic, "source", parsed.source()));
            documents.add(document);
            responses.add(IngestedDocumentResponse.builder()
                    .id(document.getId())
                    .title(parsed.title())
                    .topic(topic)
                    .build());
        }

        vectorStore.add(documents);
        return responses;
    }

    // Reference docs are formatted as:
    //   SOURCE: <name>
    //   TITLE: <title>
    //   <blank line>
    //   <body text>
    // Pulls the SOURCE/TITLE headers out and returns everything after the first blank line as
    // the content.
    private ParsedDocument extractTitleAndContent(Resource resource) throws IOException {
        String raw = resource.getContentAsString(StandardCharsets.UTF_8);
        String[] lines = raw.split("\\R", -1);

        String source = resource.getFilename();
        String title = resource.getFilename();
        int bodyStart = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("SOURCE:")) {
                source = lines[i].substring("SOURCE:".length()).trim();
            }
            if (lines[i].startsWith("TITLE:")) {
                title = lines[i].substring("TITLE:".length()).trim();
            }
            if (lines[i].isBlank()) {
                bodyStart = i + 1;
                break;
            }
        }

        String content = String.join("\n", Arrays.asList(lines).subList(bodyStart, lines.length)).trim();
        return new ParsedDocument(source, title, content);
    }

    private String topicFromFilename(String filename) {
        String base = StringUtils.hasText(filename) ? filename.replace(".txt", "") : "document";
        return base.startsWith("spring-ai-") ? base.substring("spring-ai-".length()) : base;
    }

    private record ParsedDocument(String source, String title, String content) {
    }
}
