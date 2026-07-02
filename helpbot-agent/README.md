# Helpbot Agent

The client/agent side of the Helpbot RAG system. It talks to the [MCP server](../helpbot-mcp-server/README.md) over Streamable HTTP, picks the right tools based on who's asking, and returns answers via a simple REST endpoint.

## Tech Stack

| What | Version / Library |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.0 |
| Spring AI | 2.0.0 |
| LLM | Ollama (`llama3.2:latest` by default) |
| MCP transport | Streamable HTTP |
| Auth | Spring Security (Basic Auth) |
| API docs | SpringDoc OpenAPI 2.8.8 |

## How It Works

There's one endpoint — `GET /chat?question=...` — protected by basic auth. Depending on your role, you get a different set of MCP tools:

| Role | Tools Available | What it means |
|---|---|---|
| `CUSTOMER` | `search`, `createHelpDeskTicket`, `getHelpDeskTicketsByDocumentId` | Public documents only |
| `EMPLOYEE` | `search_admin`, `createHelpDeskTicket`, `getHelpDeskTicketsByDocumentId` | Public **and** internal documents |

The routing happens in `HelpBotService` — it checks `SecurityContextHolder` for the current role and picks the right `ChatClient` bean.

### Role-Based Tool Selection

The `ToolsUtil` class handles filtering MCP tools at startup. Instead of giving the LLM access to everything the MCP server exposes, each `ChatClient` bean is configured with a specific list of tool names:

```java
// Customer chat client — public search only
ToolsUtil.selectToolsFor(mcpClients, null,
    List.of("createHelpDeskTicket", "search", "getHelpDeskTicketsByDocumentId"));

// Employee chat client — admin search (public + internal)
ToolsUtil.selectToolsFor(mcpClients, null,
    List.of("createHelpDeskTicket", "search_admin", "getHelpDeskTicketsByDocumentId"));
```

`selectToolsFor` walks through all connected MCP clients, filters tools by name, and returns only matching `ToolCallback` instances. This keeps the tool surface tight per role.

### Chat Client Config

Both clients are defined in `HelpBotChatClientConfig`. Each one gets:
- A **system prompt** (`prompts/helpbot-system.st`) — tells the LLM how to behave
- **Advisors** — `SimpleLoggerAdvisor` for debug logging, `HelpBotTokenCountAdvisor` for token usage tracking
- **Tools** — the filtered set from `ToolsUtil`

Spring AI handles the tool-call loop automatically — the LLM can invoke tools as many times as it needs to answer a question.

### Configuration

The main config lives in `application.yaml`:

```yaml
spring:
  ai:
    ollama:
      chat:
        model: llama3.2:latest          # swap this for any Ollama-compatible model
    mcp:
      client:
        streamable-http:
          connections:
            helpbot-mcp-server:
              url: http://localhost:8080  # where the MCP server is running
              endpoint: /mcp
server:
  port: 8081
```

Swap the chat model by changing `spring.ai.ollama.chat.model`. The model needs to support tool calling — not all do. `llama3.2` works well.

---

## Local Setup

### Prerequisites

- Java 25
- Docker (for Ollama, pgvector, LocalStack — all managed via Docker Compose)
- The [MCP server](../helpbot-mcp-server/README.md) running (it owns the Docker Compose infra)

### 1. Start the MCP server first

The MCP server manages all the shared infrastructure (Ollama, pgvector, LocalStack). Start it from the server module:

```bash
cd ../helpbot-mcp-server
./gradlew bootRun
```

This spins up Docker Compose with everything the agent needs.

### 2. Run the agent

```bash
cd helpbot-agent
./gradlew bootRun
```

The agent connects to:
- **Ollama** on the Docker-mapped port (auto-discovered via Docker Compose)
- **MCP server** on `http://localhost:8080/mcp`

The agent has a symlinked `compose.yaml` pointing to the root `compose.yaml` so Spring Boot Docker Compose support can discover container ports.

### 3. Test it

**Swagger UI:** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

**curl:**
```bash
# As a customer (public docs only)
curl -u customer:customer "http://localhost:8081/chat?question=What%20are%20the%20company%20policies?"

# As an employee (public + internal docs)
curl -u employee:employee "http://localhost:8081/chat?question=What%20are%20the%20company%20policies?"
```

**Postman:** Import `helpbot.postman_collection.json` from this directory — it has both requests pre-configured.

### On-Premise LLM Setup (Ollama)

For details on running Ollama locally, switching models, and embedding provider setup, check the [`main_ollama_opensource`](https://github.com/notablogger/helpbot/tree/main_ollama_opensource) branch and the [Switching Providers](../SWITCHING-PROVIDERS.md) guide.

### Default Credentials (local profile)

| Username | Password | Role |
|---|---|---|
| `customer` | `customer` | CUSTOMER |
| `employee` | `employee` | EMPLOYEE |

### Build & Test

```bash
./gradlew build          # compile + test
./gradlew test           # just tests
./gradlew bootRun        # run the agent
```

