# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Helpbot is a two-module Spring Boot RAG (Retrieval Augmented Generation) application built on the MCP (Model Context Protocol). A document ingestion server exposes tools over MCP; an agent client connects to those tools and answers questions using vector-store retrieval.

**Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, Ollama (embeddings/LLM), pgvector (PostgreSQL), S3 (document storage via LocalStack locally)

## Modules

### `helpbot-mcp-server`
The MCP server. Responsibilities:
- Reads documents from S3 using Apache Tika (`TikaDocumentReader`)
- Chunks documents and stores embeddings in pgvector (`IngestionService`)
- Exposes the vector store as MCP tools for the agent to call

Infrastructure (managed via Docker Compose and Testcontainers):
- **LocalStack** — local S3 (`helpbot-documents` bucket, init script at `localstack/init-s3-bucket.sh`)
- **Ollama** — local LLM and embeddings (`ollama/ollama:latest`, port 11434)
- **pgvector** — `pgvector/pgvector:pg16` on port 5432 (user: `myuser`, pass: `secret`, db: `mydatabase`)

### `helpbot-agent`
The MCP client/agent. Connects to the MCP server via `spring-ai-starter-mcp-client`. Currently a minimal bootstrap; AI tools and chat flows live here.

## Commands

Each module is an independent Gradle project. Run commands from within the module directory.

```bash
# MCP server
cd helpbot-mcp-server
./gradlew build          # compile + test
./gradlew test           # integration tests (needs Docker — Testcontainers)
./gradlew bootRun        # run server (Spring Boot auto-starts Docker Compose infra)

# Agent
cd helpbot-agent
./gradlew build
./gradlew test
./gradlew bootRun        # run agent (connects to MCP server)
```

Run a single test class:
```bash
./gradlew test --tests "com.helpbot.mcp.HelpbotApplicationTests"
```

## Key architecture notes

- `IngestionService.chunkAndIngest()` is the document pipeline entry point: S3 resource → Tika parse → `TokenTextSplitter` (chunk size 200, max 400 chunks) → `VectorStore`. The PDF is currently loaded from classpath (`INFO.pdf`) as a placeholder; S3 wiring is the intended path.
- `IngestionConfig` is bound to `helpbot.ingestion.*` properties for chunk size and overlap.
- Testcontainers wires real LocalStack, Ollama, and pgvector containers for integration tests — no mocks for infrastructure.
- Spring Boot Docker Compose support (`spring-boot-docker-compose`) auto-starts `compose.yaml` on `bootRun`; the `developmentOnly` scope ensures it's excluded from production builds.
- The S3 bucket name `helpbot-documents` is configured in both `application.yaml` (`helpbot.s3.bucket`) and the LocalStack init script.