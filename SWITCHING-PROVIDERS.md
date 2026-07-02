# Switching Providers

One of the nice things about Spring AI is that it abstracts away the actual providers behind interfaces. Your code talks to `VectorStore`, `EmbeddingModel`, and `ChatModel` — it doesn't care if they come from OpenAI, Ollama, or anything else. Same for the vector store.

So switching providers is mostly a matter of swapping a dependency in `build.gradle` and updating `application.yaml`. No code changes needed in most cases.

> **Looking for a fully local setup?** The [`main_ollama_opensource`](https://github.com/notablogger/helpbot/tree/main_ollama_opensource) branch has everything wired up for Ollama — no API keys needed.

This doc walks through how to swap each component.

---

## Switching the chat model

The agent module uses a chat model for reasoning and tool calling. Right now it's OpenAI `gpt-4o-mini`.

### Staying on OpenAI — just swap the model

Update `helpbot-agent/src/main/resources/application.yaml`:

```yaml
spring.ai.openai.chat.model: gpt-4o    # was gpt-4o-mini
```

That's it. Any OpenAI model that supports function calling works.

### Switching to Ollama (local)

In `helpbot-agent/build.gradle`:

```groovy
// Remove
implementation 'org.springframework.ai:spring-ai-starter-model-openai'

// Add
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
```

In `helpbot-agent/src/main/resources/application.yaml`:

```yaml
# Remove
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.chat.model: gpt-4o-mini

# Add
spring.ai.ollama.chat.model: llama3.2:latest
```

> **Important:** The model must support tool/function calling. Not all Ollama models do. Models known to work: `llama3.2`, `mistral`, `qwen2.5`, `command-r`.

### Other providers

```groovy
// Azure OpenAI
implementation 'org.springframework.ai:spring-ai-starter-model-azure-openai'

// Anthropic (Claude)
implementation 'org.springframework.ai:spring-ai-starter-model-anthropic'

// AWS Bedrock
implementation 'org.springframework.ai:spring-ai-starter-model-bedrock'

// Google Vertex AI
implementation 'org.springframework.ai:spring-ai-starter-model-vertex-ai'
```

### What you DON'T need to change

- `HelpBotChatClientConfig.java` — uses `ChatClient.Builder`, doesn't import any provider
- `HelpBotService.java` — just calls `chatClient.prompt()`, provider-agnostic
- `ToolsUtil.java` — works with `ToolCallback` interface, no chat model dependency

### Popular chat models

| Model | Provider | Tool calling | Notes |
|---|---|---|---|
| `gpt-4o-mini` | OpenAI | ✅ | What we use now, good balance of cost and quality |
| `gpt-4o` | OpenAI | ✅ | Best quality, more expensive |
| `claude-3.5-sonnet` | Anthropic | ✅ | Strong reasoning |
| `llama3.2` | Ollama | ✅ | Free, runs locally |
| `mistral` | Ollama | ✅ | Fast, solid tool calling |
| `qwen2.5` | Ollama | ✅ | Strong reasoning |
| `command-r` | Ollama | ✅ | Built for RAG |
| `gemma3` | Ollama | ❌ | No tool calling support |

### Official docs

- [Spring AI Chat Models](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
- [OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [Ollama Chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)

---

## Switching the embedding model

The MCP server uses an embedding model to convert document chunks into vectors. Right now it's OpenAI `text-embedding-3-small` (1536 dimensions).

### Staying on OpenAI — just swap the model

Update `helpbot-mcp-server/src/main/resources/application.yaml`:

```yaml
spring.ai.openai.embedding.model: text-embedding-3-large    # was text-embedding-3-small
spring.ai.vectorstore.pgvector.dimensions: 3072              # must match model output
```

### Switching to Ollama (local)

In `helpbot-mcp-server/build.gradle`:

```groovy
// Remove
implementation 'org.springframework.ai:spring-ai-starter-model-openai'

// Add
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
```

In `helpbot-mcp-server/src/main/resources/application.yaml`:

```yaml
# Remove
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.embedding.model: text-embedding-3-small

# Add
spring.ai.ollama.embedding.model: nomic-embed-text
spring.ai.vectorstore.pgvector.dimensions: 768        # match the model
```

### Other providers

```groovy
// Azure OpenAI
implementation 'org.springframework.ai:spring-ai-starter-model-azure-openai'

// AWS Bedrock (Titan, Cohere)
implementation 'org.springframework.ai:spring-ai-starter-model-bedrock'

// Google Vertex AI
implementation 'org.springframework.ai:spring-ai-starter-model-vertex-ai'

// Mistral AI
implementation 'org.springframework.ai:spring-ai-starter-model-mistral-ai'
```

### After switching: drop and re-ingest

Existing embeddings are incompatible with a different model. Drop the table and restart:

```sql
DROP TABLE IF EXISTS public.vector_store;
```

Spring AI recreates it with the new dimensions on startup.

### What you DON'T need to change

- `IngestionService.java` — uses `VectorStore` interface, doesn't know about embedding models
- `SearchTool.java` / `SearchService.java` — uses `VectorStore.similaritySearch()`, provider-agnostic
- `S3DocumentService.java` — doesn't touch embeddings at all

### Embedding dimensions reference

| Model | Provider | Dimensions |
|---|---|---|
| `text-embedding-3-small` | OpenAI | 1536 |
| `text-embedding-3-large` | OpenAI | 3072 |
| `text-embedding-ada-002` | OpenAI/Azure | 1536 |
| `nomic-embed-text` | Ollama | 768 |
| `mxbai-embed-large` | Ollama | 1024 |
| `all-minilm` | Ollama | 384 |
| `snowflake-arctic-embed` | Ollama | 1024 |
| `amazon.titan-embed-text-v2` | AWS Bedrock | 1024 |

> **pgvector HNSW index limit:** max 2000 dimensions. If your model outputs more than that (e.g., `text-embedding-3-large` at 3072), you'll need to use a different index type or pick a smaller model.

### Official docs

- [Spring AI Embedding Models](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
- [OpenAI Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html)
- [Ollama Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html)

---

## Switching the vector store

Right now we use pgvector (PostgreSQL). Spring AI supports a bunch of vector stores and they all implement the same `VectorStore` interface.

### What to change

**1. build.gradle** — swap the vector store starter:

```groovy
// Current: pgvector
implementation 'org.springframework.ai:spring-ai-starter-vector-store-pgvector'

// Options
implementation 'org.springframework.ai:spring-ai-starter-vector-store-chroma'
implementation 'org.springframework.ai:spring-ai-starter-vector-store-pinecone'
implementation 'org.springframework.ai:spring-ai-starter-vector-store-milvus'
implementation 'org.springframework.ai:spring-ai-starter-vector-store-weaviate'
implementation 'org.springframework.ai:spring-ai-starter-vector-store-redis'
implementation 'org.springframework.ai:spring-ai-starter-vector-store-elasticsearch'
implementation 'org.springframework.ai:spring-ai-starter-vector-store-neo4j'
```

**2. application.yaml** — replace the pgvector config:

```yaml
# Current: pgvector
spring.ai.vectorstore.pgvector:
  initialize-schema: true
  dimensions: 768

# Chroma example
spring.ai.vectorstore.chroma:
  url: http://localhost:8000
  collection-name: helpbot-documents

# Pinecone example
spring.ai.vectorstore.pinecone:
  api-key: ${PINECONE_API_KEY}
  environment: us-east-1
  index-name: helpbot
```

**3. compose.yaml** — if the new vector store runs in Docker, add it. If it's a managed service (Pinecone, etc.), you can remove the pgvector service.

### What you DON'T need to change

- `IngestionService.java` — calls `vectorStore.add()`, works with any implementation
- `SearchTool.java` / `SearchService.java` — calls `vectorStore.similaritySearch()`, same interface
- `S3DocumentService.java` — no vector store dependency

### Note on pgvector-specific stuff

If you're leaving pgvector, you can also remove:
- `spring-boot-starter-data-jpa` (unless you still need it for `HelpDeskTicket`)
- `org.postgresql:postgresql`
- The `spring.datasource.*` config (again, unless you need JPA for other entities)

### Official docs

- [Spring AI Vector Stores](https://docs.spring.io/spring-ai/reference/api/vectordbs.html)
- [pgvector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [Chroma](https://docs.spring.io/spring-ai/reference/api/vectordbs/chroma.html)
- [Pinecone](https://docs.spring.io/spring-ai/reference/api/vectordbs/pinecone.html)

---

## Why this works without code changes

Spring AI is designed around abstractions:

```
Your code
  → ChatModel interface             (prompt → response)
  → VectorStore interface           (add, similaritySearch, delete)
  → EmbeddingModel interface        (embed text into vectors)

Spring Boot auto-configuration
  → picks the right implementation based on which starter is on the classpath
  → configures it from application.yaml
```

So `IngestionService`, `SearchService`, and `HelpBotChatClientConfig` talk to interfaces — they never import `PgVectorStore`, `OllamaEmbeddingModel`, or `OllamaChatModel` directly. That's why swapping is just a dependency + config change.

The only thing you need to be careful about: **dimensions must match between the embedding model and the vector store**. If they don't, you'll get errors like `expected 384 dimensions, not 768`.

---

## Quick reference

| Switching... | build.gradle | application.yaml | compose.yaml | Java code | Re-ingest? |
|---|---|---|---|---|---|
| Chat model (e.g., llama3.2 → mistral) | no changes | update model name | update pull command | no changes | no |
| Chat provider (e.g., Ollama → OpenAI) | swap model starter | update model config + API key | remove ollama chat pull | no changes | no |
| Embedding model (e.g., nomic → mxbai) | no changes | update model name + dimensions | update pull command | no changes | yes |
| Embedding provider (e.g., Ollama → OpenAI) | swap model starter | update model config + dimensions | remove ollama services | no changes | yes |
| Vector store (e.g., pgvector → Pinecone) | swap vector store starter | update vector store config | swap/remove DB service | no changes | yes |
| Document storage (e.g., S3 → GCS) | swap cloud starter | update cloud config | swap LocalStack | update S3DocumentService | no |
