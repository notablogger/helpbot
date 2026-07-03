# Helpbot MCP Server

## What is this

A Spring Boot app that acts as an MCP (Model Context Protocol) server. It ingests documents, stores them as vector embeddings, and exposes tools that any MCP client can call to search through those documents or manage help desk tickets.

Built as a RAG (Retrieval Augmented Generation) backend — you throw documents at it, it chunks them up, generates embeddings via OpenAI, stores them in pgvector, and makes them searchable.

> Want to swap OpenAI for Ollama, or pgvector for Pinecone? See [SWITCHING-PROVIDERS.md](../SWITCHING-PROVIDERS.md).
>
> Want to run everything locally without an API key? Use the [`main_ollama_opensource`](https://github.com/notablogger/helpbot/tree/main_ollama_opensource) branch.

### Stack

- Java 25, Spring Boot 4.1.0, Spring AI 2.0.0
- OpenAI — embedding model (`text-embedding-3-small`, 1536 dimensions)
- pgvector (PostgreSQL 16) — stores vector embeddings + help desk tickets
- LocalStack — local S3 for document storage
- Apache Tika — parses PDFs, DOCX, PPTX, etc.
- MapStruct + Lombok — mapping and boilerplate

### MCP Tools

The server exposes these tools over Streamable HTTP at `/mcp`:

| Tool                             | What it does                                                                                  |
|----------------------------------|-----------------------------------------------------------------------------------------------|
| `search`                         | Searches the knowledge base. Only returns **public** documents (filters out `internal=true`). |
| `search_admin`                   | Same search but returns **everything** — public + internal docs.                              |
| `createHelpDeskTicket`           | Creates a help desk ticket in the database.                                                   |
| `getHelpDeskTicketsByUserId` | Fetches all help desk tickets for a user.                                                     |

### How the code is organized

```
com.helpbot.mcp/
  HelpbotMcpServerApplication.java    — entry point, @EnableScheduling

  config/
    IngestionConfig.java              — chunk-size from application.yaml
    SearchConfig.java                 — min-similarity, top-k
    WebConfig.java                    — CORS (needed for MCP Inspector)

  controller/
    IngestionController.java          — POST /api/ingest/all endpoint
  
  s3/
    S3IngestionJob.java               — scheduled job, runs ingestFromS3() every 5 min

  ingestion/
    IngestionService.java             — takes a Resource, chunks it, embeds it, stores it

  tools/
    SearchTool.java                   — MCP search tools (public + admin)
    HelpDeskTicketTool.java           — MCP ticket tools (create + query)

  service/
    HelpDeskTicketService.java        — ticket business logic
    SearchService.java                — vector store search logic (public + admin)
    S3DocumentService.java            — lists + downloads files from S3, calls ingestion


  rds/
    entity/HelpDeskTicket.java        — JPA entity
    repository/HelpDeskTicketRepository.java

  dto/
    HelpDeskTicketRequestDto.java
    HelpDeskTicketResponseDto.java

  mapper/
    HelpDeskTicketMapper.java         — MapStruct mapper
```

### Ingestion pipeline

This is the path a document takes from S3 to being searchable:

```
S3 bucket (internal/ or public/)
  → S3DocumentService.ingestFolder()     lists objects by prefix
  → S3Template.download()               downloads as Spring Resource
  → IngestionService.chunkAndIngest()    the actual processing:
      → TikaDocumentReader               parses PDF/DOCX/PPTX into text
      → TokenTextSplitter                splits into chunks (size 384, max 400 chunks)
      → adds metadata                    { "internal": true/false, "source": "filename" }
      → VectorStore.add()               generates embeddings via OpenAI, stores in pgvector
```

The metadata is important — `search` filters on `internal=false` so public users don't see internal docs. `search_admin` returns both.

The `S3IngestionJob` triggers this every 5 minutes automatically. You can also trigger it manually with `POST /api/ingest/all`.

### S3 integration

Two S3 clients are used:

- `S3Client` (AWS SDK) — for `listObjectsV2()` to list files by prefix
- `S3Template` (Spring Cloud AWS) — for `download()` and `delete()`  which returns a nice Spring `Resource`  and removed one once ingestion is completed.

Both are auto-configured by `spring-cloud-aws-starter-s3`. You don't create them manually. Spring Boot Docker Compose support auto-discovers the LocalStack port at startup.

The S3 bucket has two folders:
- `public/` — documents searchable by everyone
- `internal/` — documents only visible through `search_admin`

S3 uploads are atomic — a file is either fully uploaded or not visible at all. So the scheduled job will never pick up a half-uploaded file.

---

## Local setup

### Prerequisites

- Java 25
- Docker (for LocalStack, pgvector)
- Node.js (for MCP Inspector, optional)
- An OpenAI API key:

```bash
export OPENAI_API_KEY=sk-...
```

### compose.yaml

Everything runs via Docker Compose. Spring Boot auto-starts it when you do `./gradlew bootRun`.

Services:
- **LocalStack** — local S3 on port 4566
- **pgvector** — PostgreSQL 16 with vector extension on port 5432

Ports are mapped dynamically — Spring Boot discovers them automatically.

### Where to put documents

Drop your files here:

```
helpbot-mcp-server/localstack/documents/
  public/         ← public docs go here
  internal/       ← internal/confidential docs go here
```

When LocalStack starts, the init script (`localstack/init-s3-bucket.sh`) creates the `helpbot-documents` bucket and uploads everything from this folder into S3, preserving the `public/` and `internal/` prefix structure.

If LocalStack is already running and you added new files, recreate the container to re-run the init script:

```bash
docker compose up -d --force-recreate localstack
```

Or upload directly to S3:

```bash
# find the localstack port
docker compose ps localstack

# upload
aws --endpoint-url=http://localhost:<PORT> s3 cp myfile.pdf s3://helpbot-documents/public/myfile.pdf
```

### Steps to run

1. Set your OpenAI API key:

```bash
export OPENAI_API_KEY=sk-...
```

2. Start the server (this starts Docker Compose automatically):

```bash
cd helpbot-mcp-server
./gradlew bootRun
```

3. Wait for it — first time takes a while as it ingests documents.

4. The ingestion job runs immediately on startup and then every 5 minutes. You can also trigger it manually:

```bash
curl -X POST http://localhost:8080/api/ingest/all
```

5. Check the S3 bucket contents:

```bash
docker compose exec localstack awslocal s3 ls s3://helpbot-documents/ --recursive
```

6. Check the database:

```bash
docker compose exec pgvector psql -U myuser -d mydatabase -c "\dt public.*"
```

### MCP Inspector

MCP Inspector is a browser-based tool for testing MCP servers. Run it with:

```bash
npx @modelcontextprotocol/inspector
```

Then connect to your server:
- **Transport:** Streamable HTTP
- **URL:** `http://localhost:8080/mcp`

You should see all 4 tools listed. Try calling `search` with a question to test it.

Note: MCP Inspector runs in the browser, so the server needs CORS enabled. That's what `WebConfig.java` does — it allows all origins and exposes the `Mcp-Session-Id` header which the Streamable HTTP protocol needs.

### Gotchas

- If you restart LocalStack but not the server, you'll get `Connection refused` errors because the port changed. Restart the server too.
- `ddl-auto: update` means Hibernate creates missing tables on startup but doesn't drop existing ones. If you change an entity, you might need to drop the table manually.
- The embedding model outputs 1536-dimensional vectors. The `dimensions: 1536` in `application.yaml` must match. If you switch models, update both. See [SWITCHING-PROVIDERS.md](../SWITCHING-PROVIDERS.md).
- S3 data lives inside the LocalStack container and is lost on recreate. Your source of truth is the `localstack/documents/` folder.
- Make sure `OPENAI_API_KEY` is set before starting the server, or you'll get authentication errors.
