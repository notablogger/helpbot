# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Helpbot is a two-module Spring Boot RAG (Retrieval Augmented Generation) application built on the MCP (Model Context Protocol). `helpbot-mcp-server` ingests documents from S3, embeds and stores them in pgvector, and exposes MCP tools. `helpbot-agent` connects to those tools as an MCP client, picks a tool subset based on the caller's role, and answers questions via a chat endpoint.

**Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, OpenAI (`gpt-4o-mini` chat / `text-embedding-3-small` embeddings), pgvector (PostgreSQL 16), S3 (LocalStack locally), Apache Tika.

This is the `main` branch — OpenAI, needs `OPENAI_API_KEY`. A fully local variant using Ollama for both chat and embeddings (no API key) lives on the `main_ollama_opensource` branch; do not assume Ollama config applies here even though some test/build files retain Ollama plumbing (see Gotchas).

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

Exercise the chat endpoint (`local` profile seeds `customer/customer` and `employee/employee` — see `SecurityConfig`):
```bash
curl -u customer:customer "http://localhost:8081/chat?question=What%20are%20the%20company%20policies?"
curl -u employee:employee "http://localhost:8081/chat?question=What%20are%20the%20company%20policies?"
```

Swagger UI for the agent: `http://localhost:8081/swagger-ui.html`. MCP Inspector against the server: `npx @modelcontextprotocol/inspector`, Streamable HTTP, `http://localhost:8080/mcp`.

## Architecture

### Role-based tool exposure (the core mechanism in `helpbot-agent`)

`HelpBotService.chat()` reads the caller's role off `SecurityContextHolder` (populated by HTTP Basic auth) and dispatches to one of two `ChatClient` beans defined in `HelpBotChatClientConfig`:

- `helpBotChatClient` (CUSTOMER) — tools: `search`, `createHelpDeskTicket`, `getHelpDeskTicketsByUserId`
- `helpBotInternalChatClient` (EMPLOYEE) — tools: `search_admin`, `createHelpDeskTicket`, `getHelpDeskTicketsByUserId`

The tool sets are *not* enforced by prompting — `ToolsUtil.selectToolsFor()` filters the MCP server's advertised tools down to an explicit allow-list per client at bean-construction time, so each `ChatClient` physically cannot see tools outside its list. `search` vs `search_admin` is itself enforced server-side (see below), so the split is defense in depth, not the only guard.

Both clients share the same system prompt (`prompts/helpbot-system.st`) and advisor chain: `SimpleLoggerAdvisor`, `HelpBotTokenCountAdvisor` (logs token usage per call), `MessageChatMemoryAdvisor` keyed by username (`CONVERSATION_ID` = the authenticated user). The user-turn template (`prompts/user-system.st`) injects `userName`, `question`, and `role` into every prompt.

### Ingestion pipeline (`helpbot-mcp-server`)

```
S3 bucket, prefixes public/ and internal/
  → S3IngestionJob (@Scheduled, every 5 min) or POST /api/ingest/all
  → S3DocumentService.ingestFromS3()      lists objects per prefix, downloads via S3Template, deletes from S3 after ingest
  → IngestionService.chunkAndIngest()     TikaDocumentReader → TokenTextSplitter (chunk-size from helpbot.ingestion.chunk-size, max 400 chunks) → VectorStore.add()
```

Every chunk gets `internal` (boolean) and `source` (filename) metadata. This is the only access-control signal in the system: `SearchService.searchPublic()` filters `internal=false`; `SearchService.searchAll()` (backing `search_admin`) filters `internal in (true,false)`. There's no per-document ACL beyond this flag — a document's S3 prefix (`public/` vs `internal/`) at ingest time is what sets it.

S3 objects are deleted immediately after successful ingestion — the bucket is a transient inbox, not the source of truth. For local dev, the source of truth is `helpbot-mcp-server/localstack/documents/{public,internal}/`, uploaded into LocalStack by `localstack/init-s3-bucket.sh` on container start; re-upload requires `docker compose up -d --force-recreate localstack`.

### MCP surface

`helpbot-mcp-server` exposes 4 tools via `@McpTool` (Spring AI's MCP server support derives snake/camel case from the method unless overridden — these use explicit names): `search`, `search_admin`, `createHelpDeskTicket`, `getHelpDeskTicketsByUserId`. Help desk tickets are plain JPA (`HelpDeskTicket` entity, `ddl-auto: update`) — no vector search involved, unrelated to the RAG pipeline.

`helpbot-agent` connects over Streamable HTTP (`spring.ai.mcp.client.streamable-http.connections.helpbot-mcp-server.url`) and discovers tools dynamically via `McpSyncClient` — `ToolsUtil` re-filters that list per chat client rather than the client being statically bound to a subset.

### Config namespaces

- `spring.ai.*` — model/vector-store wiring (both modules)
- `helpbot.ingestion.chunk-size`, `helpbot.search.{top-k,min-similarity}`, `helpbot.s3.bucket` — this app's own settings, bound via `@ConfigurationProperties` (`IngestionConfig`, `SearchConfig`)

## Gotchas

- `helpbot-mcp-server`'s `TestcontainersConfiguration` still spins up an `OllamaContainer` even on this OpenAI branch (kept for parity with `main_ollama_opensource`) — it's unused by the app under test but still costs container startup time; don't read its presence as a signal that this branch talks to Ollama.
- Embedding dimensions (`spring.ai.vectorstore.pgvector.dimensions: 1536`) must match the embedding model's output. Changing `spring.ai.openai.embedding.model` without updating `dimensions` breaks ingestion — see `SWITCHING-PROVIDERS.md`.
- `ddl-auto: update` never drops columns/tables — entity changes that remove/rename fields need a manual schema fix locally.
- The agent's `compose.yaml` is a symlink to the root one so Spring Boot Docker Compose port-discovery works there too, but the MCP server owns actually starting the containers — start the server before the agent.
- `SWITCHING-PROVIDERS.md` documents exactly which files are provider-agnostic (`IngestionService`, `SearchService`/`SearchTool`, `HelpBotChatClientConfig`) vs. what needs to change when swapping chat model, embedding model, or vector store — check it before touching provider wiring.
