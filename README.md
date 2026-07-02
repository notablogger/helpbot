# Helpbot

A two-module Spring Boot RAG (Retrieval Augmented Generation) application built on the MCP (Model Context Protocol). You throw documents at it, it chunks them, embeds them, and makes them searchable through a chat interface — with role-based access so customers only see public docs and employees see everything.

## Branches

| Branch | LLM Provider | When to use |
|---|---|---|
| **`main`** (this) | OpenAI | Default. You need an `OPENAI_API_KEY`. |
| [`main_ollama_opensource`](https://github.com/notablogger/helpbot/tree/main_ollama_opensource) | Ollama (local) | Fully on-premise, no API key needed. Runs LLM and embeddings locally via Docker. |

## Architecture

```
┌──────────────────────┐         MCP (Streamable HTTP)         ┌──────────────────────────┐
│   helpbot-agent      │ ────────────────────────────────────▶  │   helpbot-mcp-server     │
│                      │                                        │                          │
│  REST endpoint       │                                        │  Vector search tools     │
│  Basic Auth          │                                        │  Help desk ticket tools  │
│  Role-based routing  │                                        │  Document ingestion      │
│  OpenAI (gpt-4o-mini)│                                        │  OpenAI (embeddings)     │
└──────────────────────┘                                        │  pgvector · S3           │
                                                                └──────────────────────────┘
```

**Stack:** Java 25 · Spring Boot 4.1.0 · Spring AI 2.0.0 · OpenAI · pgvector · LocalStack (S3) · Apache Tika

## Modules

### [helpbot-mcp-server](helpbot-mcp-server/README.md)

The backend. Reads documents from S3, chunks them with Tika, generates embeddings via OpenAI (`text-embedding-3-small`), stores them in pgvector, and exposes MCP tools for search and ticket management. This is where all the RAG infrastructure lives.

**Tools exposed:**
- `search` — public document search
- `search_admin` — public + internal document search
- `createHelpDeskTicket` — create a help desk ticket
- `getHelpDeskTicketsByDocumentId` — query tickets by document

### [helpbot-agent](helpbot-agent/README.md)

The frontend/client. Connects to the MCP server, provides a `GET /chat` REST endpoint with basic auth. Based on the user's role (CUSTOMER or EMPLOYEE), it picks the right set of tools and routes the question through the appropriate chat client. The LLM (`gpt-4o-mini`) does the reasoning and tool calling.

## Prerequisites

- Java 25
- Docker (for LocalStack, pgvector)
- An OpenAI API key — set it as an environment variable:

```bash
export OPENAI_API_KEY=sk-...
```

> **Want to run fully locally without an API key?** Use the [`main_ollama_opensource`](https://github.com/notablogger/helpbot/tree/main_ollama_opensource) branch instead — it uses Ollama for both chat and embeddings, everything runs in Docker.

## Quick Start

```bash
# 1. Set your OpenAI API key
export OPENAI_API_KEY=sk-...

# 2. Start the MCP server (auto-starts Docker Compose infra)
cd helpbot-mcp-server
./gradlew bootRun

# 3. In another terminal, start the agent
cd helpbot-agent
./gradlew bootRun

# 4. Ask a question
curl -u customer:customer "http://localhost:8081/chat?question=What%20are%20the%20company%20policies?"
```

Check each module's README for detailed setup instructions.

## Switching Providers

Spring AI abstracts away the actual providers — your code talks to `VectorStore`, `EmbeddingModel`, and `ChatModel` interfaces. Swapping OpenAI for Ollama, or pgvector for Pinecone, is mostly a dependency + config change. No code changes.

See **[SWITCHING-PROVIDERS.md](SWITCHING-PROVIDERS.md)** for the full guide.
