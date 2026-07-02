# Helpbot

A two-module Spring Boot RAG (Retrieval Augmented Generation) application built on the MCP (Model Context Protocol). You throw documents at it, it chunks them, embeds them, and makes them searchable through a chat interface — with role-based access so customers only see public docs and employees see everything.

## Architecture

```
┌──────────────────────┐         MCP (Streamable HTTP)         ┌──────────────────────────┐
│   helpbot-agent      │ ────────────────────────────────────▶  │   helpbot-mcp-server     │
│                      │                                        │                          │
│  REST endpoint       │                                        │  Vector search tools     │
│  Basic Auth          │                                        │  Help desk ticket tools  │
│  Role-based routing  │                                        │  Document ingestion      │
│  Ollama (chat LLM)   │                                        │  Ollama (embeddings)     │
└──────────────────────┘                                        │  pgvector · S3           │
                                                                └──────────────────────────┘
```

**Stack:** Java 25 · Spring Boot 4.1.0 · Spring AI 2.0.0 · Ollama · pgvector · LocalStack (S3) · Apache Tika

## Modules

### [helpbot-mcp-server](helpbot-mcp-server/README.md)

The backend. Reads documents from S3, chunks them with Tika, generates embeddings via Ollama, stores them in pgvector, and exposes MCP tools for search and ticket management. This is where all the RAG infrastructure lives.

**Tools exposed:**
- `search` — public document search
- `search_admin` — public + internal document search
- `createHelpDeskTicket` — create a help desk ticket
- `getHelpDeskTicketsByDocumentId` — query tickets by document

### [helpbot-agent](helpbot-agent/README.md)

The frontend/client. Connects to the MCP server, provides a `GET /chat` REST endpoint with basic auth. Based on the user's role (CUSTOMER or EMPLOYEE), it picks the right set of tools and routes the question through the appropriate chat client. The LLM (`llama3.2`) does the reasoning and tool calling.

## Switching Providers

Spring AI abstracts away the actual providers — your code talks to `VectorStore`, `EmbeddingModel`, and `ChatModel` interfaces. Swapping Ollama for OpenAI, or pgvector for Pinecone, is mostly a dependency + config change. No code changes.

See **[SWITCHING-PROVIDERS.md](SWITCHING-PROVIDERS.md)** for the full guide.

## Quick Start

```bash
# 1. Start the MCP server (auto-starts Docker Compose infra)
cd helpbot-mcp-server
./gradlew bootRun

# 2. In another terminal, start the agent
cd helpbot-agent
./gradlew bootRun

# 3. Ask a question
curl -u customer:customer "http://localhost:8081/chat?question=What%20are%20the%20company%20policies?"
```

Check each module's README for detailed setup instructions.

