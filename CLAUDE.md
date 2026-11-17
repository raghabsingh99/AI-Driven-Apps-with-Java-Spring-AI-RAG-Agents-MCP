# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A Spring Boot + Spring AI demo/course project (course: "Build AI-Driven Apps with Java, Spring AI, RAG, Agents, MCP"). It's a sandbox of small, independent REST endpoints illustrating individual Spring AI concepts: prompt templates, system prompts, conversation history (in-memory and JDBC-persisted), streaming, structured output, prompt chaining, prompt-injection defenses, few-shot prompting, advisors (logging/safety/mutation/disclaimer/timing), embeddings/semantic search, and a persistent RAG vector store. There is no auth. Most endpoints are self-contained demos hitting the OpenAI chat API through Spring AI's `ChatClient`; the memory and RAG packages additionally talk to real databases.

Not currently a git repository.

## Build, run, test

This project uses the Maven wrapper; always invoke it with `./mvnw` (or `.\mvnw.cmd` from PowerShell), not a system `mvn`.

```
./mvnw spring-boot:run              # run the app (port 8080 by default)
./mvnw clean package                # build
./mvnw test                         # run all tests
./mvnw test -Dtest=DemoApplicationTests          # run a single test class
./mvnw test -Dtest=DemoApplicationTests#contextLoads  # run a single test method
```

PowerShell equivalents use `.\mvnw.cmd` in place of `./mvnw`.

The app requires an `OPENAI_API_KEY` environment variable (see Configuration below) — `spring-boot:run` and `test` (via `@SpringBootTest` context load) will fail without it. It also requires a reachable MySQL server (chat memory) and MariaDB server (vector store) and a local Ollama instance serving an embedding model — see Configuration.

## Configuration

`src/main/resources/application.properties`:
- `spring.ai.openai.api-key=${OPENAI_API_KEY}` — must be set in the environment before running. Chat model pinned to `gpt-4o-mini`, temperature `0.7`.
- `spring.ai.model.chat=openai` / `spring.ai.model.embedding=ollama` — pin which provider backs each model type. Required because both the OpenAI and Ollama Spring AI starters are on the classpath (for chat and embeddings respectively), which is otherwise ambiguous.
- `spring.ai.ollama.base-url` + `spring.ai.ollama.embedding.options.model=nomic-embed-text` — Ollama must be running locally and have this model pulled.
- `spring.datasource.*` — the **primary** datasource, a MySQL server at `localhost:3306`, database `chatmemory`. Backs the JDBC `ChatMemoryRepository` used by `PersistentChatMemoryDemo`/`ChatManagementController` (schema auto-created via `spring.ai.chat.memory.repository.jdbc.initialize-schema=always`).
- `app.vectorstore.datasource.*` — a **second**, independent datasource, a MariaDB server at `localhost:3307`, database `vectorstore`. Backs the persistent `VectorStore` in `com.course.demo.rag` (MariaDB specifically because it supports a native `VECTOR` column type and distance functions that MySQL lacks). This is a different server/engine from the chat-memory datasource above, not an alternate name for it.

### Two-datasource wiring (important when touching `com.course.demo.rag`)

Defining a second `DataSource` means **both** must be defined explicitly in `VectorStoreDataSourceConfig`, since Spring Boot's auto-configured `DataSource`/`JdbcTemplate` backs off entirely once any manual `DataSource` bean exists. The chat-memory beans are `@Primary` so the rest of the app (`JdbcChatMemoryRepository`, etc.) is unaffected. Each `DataSourceProperties` parameter is explicitly `@Qualifier`'d — without it, Spring resolves an ambiguous-by-type parameter via `@Primary` *before* falling back to parameter-name matching, so the vector-store `DataSource` bean would otherwise silently receive the chat-memory `DataSourceProperties` instead of its own (a real bug hit during development).

`MariaDbStoreAutoConfiguration` is explicitly excluded in `DemoApplication` because `VectorStoreConfig` defines the `VectorStore` bean manually against the qualified vector-store `JdbcTemplate`. Leaving the auto-configuration enabled causes a `BeanDefinitionOverrideException`-adjacent conflict: its `@ConditionalOnMissingBean` doesn't back off, because its declared return type (`MariaDBVectorStore`) doesn't type-match the manual bean's declared return type (`VectorStore`).

## Architecture

- **No service/repository layers for the prompting demos.** Each `@RestController` under `controller/`, `streaming/`, `structured/`, and `advisor/` builds and calls a `ChatClient` directly inline. `ChatClient.Builder` is injected (per standard conventions below) and a fresh client is built per request/method — there's no shared singleton `ChatClient` bean. The `memory/` and `rag/` packages are the exception: they hold real persistence configuration (`DataSource`/`JdbcTemplate`/`VectorStore` beans) because their whole point is demonstrating persistence.
- **Prompt templates live as classpath resources**, not inline strings (with a couple of intentional inline exceptions for comparison). They sit in `src/main/resources/prompts/*.st` and are injected into controllers via `@Value("classpath:/prompts/<name>.st")` as a `Resource`, then rendered with Spring AI's `PromptTemplate` (`.render()` for no variables, `.render(Map.of(...))` or `.create(Map.of(...))` when the template has `{placeholders}`).
- **Package-per-concept layout**, one dedicated package per concept with its controller(s) and any supporting types, rather than growing any single controller further:
  - `controller/` — `TechAdvisorController` (`/api/tech-advisor/**`), the main teaching surface for prompting techniques: default/system prompts, persona-templated system prompts, manual conversation history (`List<Message>` with `SystemMessage`/`UserMessage`/`AssistantMessage`), prompt variables (via `PromptTemplate.create` vs. inline `.param(...)` on `.user(...)`), role prompting, output-format constraints, structured-section constraints, prompt-injection resistance, few-shot classification, and a 3-step prompt-chaining example (`/chained-blog-post`: outline → draft → polish, three sequential `ChatClient` calls reusing one client instance).
  - `streaming/` — `StreamingDemo` (`/api/streamed-demo/**`), reactive streaming via `.stream().content()` returning `Flux<String>`, paired with `src/main/resources/static/streaming-demo.html` (plain HTML/JS, `fetch` + `ReadableStream`, served statically with no templating engine).
  - `structured/` — `StructuredDemo` (`/api/structured-demo/**`), typed structured output: `.call().entity(CourseSummary.class)` has Spring AI coerce the model's response directly into the `CourseSummary` record.
  - `advisor/` — `AdvisorsDemo` (`/api/advisors-demo/**`) plus custom `Advisor` implementations (`CustomLoggerAdvisor`, `DisclaimerAdvisor`, `PromptEnhancerAdvisor`, `TimingAdvisor`). Demonstrates both built-in advisors (`SimpleLoggerAdvisor`, `SafeGuardAdvisor`) and hand-written ones, including advisor ordering: `Advisor#getOrder` ascending = outer → inner, so `before()` runs outer→inner and `after()` runs inner→outer (see the comment above `/chained` for a worked example).
  - `memory/` — conversation history, two parallel implementations for comparison:
    - `ChatMemoryDemo` (`/api/chat-memory-demo/**`) — in-memory only (`InMemoryChatMemoryRepository`), a single `ChatMemory` instance held as a field so history persists across calls within the same JVM run but is lost on restart.
    - `PersistentChatMemoryDemo` (`/api/persistent-chat-memory-demo/**`) — same shape, but backed by the JDBC `ChatMemoryRepository` auto-configured against the primary MySQL datasource, so history survives restarts. A fresh `MessageWindowChatMemory` wrapper is built per call (cheap — the state lives in the DB), capped at 10 messages.
    - Both default `conversationId` to the caller's `HttpSession` id when not supplied, so each browser session is automatically pinned to its own conversation with no client-side bookkeeping, while still accepting an explicit `conversationId` for curl/Postman testing.
    - `ChatManagementController` (`/api/chat-management/**`) — a management surface over the same JDBC `ChatMemoryRepository`: inspect/invalidate the current `HttpSession`, and list/view/delete conversations by id. There's no `User` entity or auth in this project, so "users" are simply represented by `conversationId`.
    - `memory/embeddings/SemanticSearchController` (`/api/semantic-search/**`) — computes cosine similarity between two texts' embeddings via the auto-configured `EmbeddingModel`, as a standalone illustration of the metric vector stores use internally for similarity search.
  - `rag/` — a persistent RAG vector store, on its own MariaDB datasource (see Configuration above):
    - `VectorStoreConfig` defines the `VectorStore` bean (`MariaDBVectorStore`), seeds it with a handful of sample documents on first run only (guarded by a row-count check against `MariaDBVectorStore.DEFAULT_TABLE_NAME`, to avoid re-seeding duplicates on every restart), and manually invokes `afterPropertiesSet()` on it so the schema exists before that count query runs.
    - `VectorStoreController` (`/api/vector-store/**`) — `/search` (plain similarity search), `/search/by-topic` (adds a metadata filter via `FilterExpressionBuilder().eq("topic", ...)`), and `POST /documents` to add a new document with optional `topic` metadata.
- All endpoints default to `text/plain` (or return a record/DTO serialized as JSON where noted above) and expose `@RequestParam(defaultValue = ...)` so every endpoint is directly curl/browser-testable with no request body, e.g. `GET /api/tech-advisor/role-prompting?prompt=...`.

When adding a new prompting-technique endpoint, follow the existing pattern: put the system/user template text in `src/main/resources/prompts/*.st`, inject it with `@Value("classpath:/prompts/...")`, and keep the controller method self-contained (build the client, render the template, call, return `.content()`) rather than factoring out shared helpers — the point of this codebase is side-by-side comparison of techniques, not DRY production code. When adding to `memory/` or `rag/`, the persistence wiring (datasources, `VectorStore`/`ChatMemoryRepository` beans) is the one place shared configuration is expected and appropriate.
