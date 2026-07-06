# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Helpbot is a two-module Spring Boot RAG (Retrieval Augmented Generation) application built on the MCP (Model Context Protocol). `helpbot-mcp-server` ingests documents from S3, embeds and stores them in pgvector, and exposes MCP tools. `helpbot-agent` connects to those tools as an MCP client, picks a tool subset based on the caller's role, and answers questions via a chat endpoint.

**Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, OpenAI (`gpt-4o-mini` chat / `text-embedding-3-small` embeddings), pgvector (PostgreSQL 16), S3 (LocalStack locally), Apache Tika.

This is the `main` branch — OpenAI, needs `OPENAI_API_KEY`. A fully local variant using Ollama for both chat and embeddings (no API key) lives on the `main_ollama_opensource` branch; do not assume Ollama config applies here even though some test/build files retain Ollama plumbing (see Gotchas).

For a cross-cutting walkthrough (RAG, ingestion, MCP, the agent loop, chat memory, and the agentic harness), see [ARCHITECTURE.md](ARCHITECTURE.md). For token-cost tradeoffs and where semantic caching would help, see [TOKENOMICS.md](TOKENOMICS.md).

## Commands

Each module is an independent Gradle project (own `settings.gradle`, own wrapper). Run commands from within the module directory. Gradle wrapper uses Gradle 9.5.1 / Java 25 toolchain.

```bash
# MCP server (needs OPENAI_API_KEY; Spring Boot auto-starts compose.yaml: LocalStack + pgvector)
cd helpbot-mcp-server
./gradlew build          # compile + test
./gradlew test           # tests — needs Docker (Testcontainers: LocalStack, pgvector, Ollama container)
./gradlew bootRun

# Agent (needs OPENAI_API_KEY; needs the MCP server already running on :8080)
cd helpbot-agent
./gradlew build
./gradlew test
./gradlew bootRun         # serves on :8081
```

Run a single test class:
```bash
./gradlew test --tests "com.helpbot.mcp.HelpbotApplicationTests"
```

Manually trigger ingestion (instead of waiting for the 5-min scheduled job):
```bash
curl -X POST http://localhost:8080/api/ingest/all
```

Exercise the chat endpoint (`local` profile seeds `john/customer` (CUSTOMER) and `joana/employee` (EMPLOYEE) — see `SecurityConfig`; the READMEs used to say `customer/customer` and `employee/employee`, which don't exist):
```bash
curl -u john:customer "http://localhost:8081/chat?question=What%20are%20the%20company%20policies?"
curl -u joana:employee "http://localhost:8081/chat?question=What%20are%20the%20company%20policies?"
```

Swagger UI for the agent: `http://localhost:8081/swagger-ui.html`. MCP Inspector against the server: `npx @modelcontextprotocol/inspector`, Streamable HTTP, `http://localhost:8080/mcp`.

## Architecture

Full walkthrough in [ARCHITECTURE.md](ARCHITECTURE.md) / [AGENTIC-HARNESS.md](AGENTIC-HARNESS.md) / [TOKENOMICS.md](TOKENOMICS.md). Quick reference:

**Role-based tool exposure** (`helpbot-agent`):
- `HelpBotService.chat()` reads role off `SecurityContextHolder`, dispatches to one of two `ChatClient` beans (`HelpBotChatClientConfig`):
  - `helpBotChatClient` (CUSTOMER) — tools: `search`, `createHelpDeskTicket`, `getHelpDeskTicketsByUserId`
  - `helpBotInternalChatClient` (EMPLOYEE) — tools: `search_admin`, `createHelpDeskTicket`, `getHelpDeskTicketsByUserId`
- Enforced by `ToolsUtil.selectToolsFor()` filtering the tool-list per client at bean-construction time (not by prompting) — `search`/`search_admin` also has a server-side filter, so it's defense in depth.
- Both clients share `prompts/helpbot-system.st` + advisor chain (`SimpleLoggerAdvisor`, `HelpBotTokenCountAdvisor`, `MessageChatMemoryAdvisor` keyed by username). `prompts/user-system.st` injects `userName`/`question`/`role`.
- `HelpBotService.chat()` binds `userName` on the *system* spec too (`.system(sys -> sys.param(...))`) — `defaultSystem()` only sets text, so without this the `{userName}` placeholder is sent unrendered (verified with a stub `ChatModel`; Spring AI doesn't throw on unbound placeholders here, it just leaves them literal).

**Ingestion pipeline** (`helpbot-mcp-server`):
```
S3 bucket, prefixes public/ and internal/
  → S3IngestionJob (@Scheduled, every 5 min) or POST /api/ingest/all
  → S3DocumentService.ingestFromS3()      lists per prefix, downloads via S3Template, deletes from S3 after ingest
  → IngestionService.chunkAndIngest()     TikaDocumentReader → TokenTextSplitter (helpbot.ingestion.chunk-size, max 400 chunks) → delta upsert into VectorStore
```
- Every chunk gets `internal` (bool) + `source` (filename) + `contentHash` metadata — `internal`/`source` are the only access-control signal in the system (`SearchService.searchPublic()`/`searchAll()` filter on `internal`).
- Each chunk's id is a deterministic hash of `(source, chunk index, content)` (`JdkSha256HexIdGenerator`). Before embedding, `IngestionService` looks up the file's existing chunk ids (filtered `similaritySearch` on `source`) and only embeds+upserts ids not already present; existing ids no longer produced by the new split are deleted as orphans. Re-ingesting an unchanged file costs one lookup call and zero embedding calls.
- S3 is a transient inbox (deleted after ingest); local source of truth is `helpbot-mcp-server/localstack/documents/{public,internal}/` (re-upload needs `docker compose up -d --force-recreate localstack`).

**MCP surface**:
- `helpbot-mcp-server` exposes 4 `@McpTool`s (explicit names — Spring AI otherwise derives camelCase): `search`, `search_admin`, `createHelpDeskTicket`, `getHelpDeskTicketsByUserId`. Tickets are plain JPA, unrelated to the RAG pipeline.
- `helpbot-agent` connects over Streamable HTTP, discovers tools via `McpSyncClient`; `ToolsUtil` re-filters per chat client rather than the client being statically bound.

### Config namespaces

- `spring.ai.*` — model/vector-store wiring (both modules)
- `helpbot.ingestion.chunk-size`, `helpbot.search.{top-k,min-similarity}`, `helpbot.s3.bucket` — this app's own settings, bound via `@ConfigurationProperties` (`IngestionConfig`, `SearchConfig`)

## CI

`.github/workflows/ci.yml` runs two independent jobs (one per module, each with its own working directory and Gradle wrapper), on push to `main`/`claude/**` and on PRs into `main`:

- `mcp-server` — `./gradlew build`, tests included (Testcontainers spins up LocalStack/pgvector/Ollama against the runner's Docker daemon).
- `agent` — `./gradlew build -x test compileTestJava`, tests excluded. See the MCP-client-startup gotcha above for why.

Both jobs set a placeholder `OPENAI_API_KEY` env var so the Spring context can resolve the property; no real OpenAI call happens in CI.

## Gotchas

- `helpbot-mcp-server`'s `TestcontainersConfiguration` still spins up an `OllamaContainer` even on this OpenAI branch (kept for parity with `main_ollama_opensource`) — it's unused by the app under test but still costs container startup time; don't read its presence as a signal that this branch talks to Ollama.
- Embedding dimensions (`spring.ai.vectorstore.pgvector.dimensions: 1536`) must match the embedding model's output. Changing `spring.ai.openai.embedding.model` without updating `dimensions` breaks ingestion — see `SWITCHING-PROVIDERS.md`.
- `ddl-auto: update` never drops columns/tables — entity changes that remove/rename fields need a manual schema fix locally.
- The agent's `compose.yaml` is a symlink to the root one so Spring Boot Docker Compose port-discovery works there too, but the MCP server owns actually starting the containers — start the server before the agent.
- `SWITCHING-PROVIDERS.md` documents exactly which files are provider-agnostic (`IngestionService`, `SearchService`/`SearchTool`, `HelpBotChatClientConfig`) vs. what needs to change when swapping chat model, embedding model, or vector store — check it before touching provider wiring.
- `helpbot-agent`'s Spring AI MCP client connects to the MCP server URL **eagerly and synchronously at context startup**, and fails application/test startup hard if it can't reach it (upstream limitation, not configurable here — see [spring-ai#3232](https://github.com/spring-projects/spring-ai/issues/3232)). `HelpbotAgentApplicationTests` (`@SpringBootTest`, no mocking) will only pass with a real `helpbot-mcp-server` reachable at `http://localhost:8080/mcp`; CI does not run it for this reason (see `.github/workflows/ci.yml`).
