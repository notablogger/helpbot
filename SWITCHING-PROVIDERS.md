# Switching Providers

One of the nice things about Spring AI is that it abstracts away the actual providers behind interfaces. Your code talks to `VectorStore`, `EmbeddingModel`, and `ChatModel` — it doesn't care if the embeddings come from Ollama, OpenAI, or anything else. Same for the vector store and chat model.

So switching providers is mostly a matter of swapping a dependency in `build.gradle` and updating `application.yaml`. No code changes needed in most cases.

This doc walks through how to swap each component.

---

## Switching the chat model

The agent module uses a chat model for reasoning and tool calling. Right now it's `llama3.2:latest` running on Ollama.

### What to change

**1. Staying on Ollama — just swap the model:**

Update `helpbot-agent/src/main/resources/application.yaml`:

```yaml
spring.ai.ollama.chat.model: llama3.2:latest    # change this
```

And update the model pull in `compose.yaml`:

```yaml
ollama-model-pull-chat:
  command: ["pull", "mistral:latest"]            # new model
```

> **Important:** The model must support tool/function calling. Not all Ollama models do. Models known to work: `llama3.2`, `mistral`, `qwen2.5`, `command-r`.

**2. Switching provider entirely (e.g., Ollama → OpenAI):**

In `helpbot-agent/build.gradle`:

```groovy
// Remove
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'

// Add
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

In `helpbot-agent/src/main/resources/application.yaml`:

```yaml
# Remove
spring.ai.ollama.chat.model: llama3.2:latest

# Add
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.chat.model: gpt-4o
```

### What you DON'T need to change

- `HelpBotChatClientConfig.java` — uses `ChatClient.Builder`, doesn't import any provider
- `HelpBotService.java` — just calls `chatClient.prompt()`, provider-agnostic
- `ToolsUtil.java` — works with `ToolCallback` interface, no chat model dependency

### Popular chat models

| Model | Provider | Tool calling | Notes |
|---|---|---|---|
| `llama3.2` | Ollama | ✅ | Good default, what we use now |
| `mistral` | Ollama | ✅ | Fast, solid tool calling |
| `qwen2.5` | Ollama | ✅ | Strong reasoning |
| `command-r` | Ollama | ✅ | Built for RAG |
| `gemma3` | Ollama | ❌ | No tool calling support |
| `gpt-4o` | OpenAI | ✅ | Best quality, paid |
| `gpt-4o-mini` | OpenAI | ✅ | Cheaper, still good |
| `claude-3.5-sonnet` | Anthropic | ✅ | Strong reasoning, paid |

### Official docs

- [Spring AI Chat Models](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
- [Ollama Chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
- [OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)

---

## Switching the embedding model

The MCP server uses an embedding model to convert document chunks into vectors. Right now it's `nomic-embed-text` running on Ollama (768 dimensions).

### What to change

**1. Staying on Ollama — just swap the model:**

Update `helpbot-mcp-server/src/main/resources/application.yaml`:

```yaml
spring.ai.ollama.embedding.model: nomic-embed-text   # change this
spring.ai.vectorstore.pgvector.dimensions: 768        # must match model output
```

And update the model pull in `compose.yaml`:

```yaml
ollama-model-pull-embedding:
  command: ["pull", "mxbai-embed-large"]              # new model
```

**2. Switching provider entirely (e.g., Ollama → OpenAI):**

In `helpbot-mcp-server/build.gradle`:

```groovy
// Remove
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'

// Add
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

In `helpbot-mcp-server/src/main/resources/application.yaml`:

```yaml
# Remove
spring.ai.ollama.embedding.model: nomic-embed-text

# Add
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.embedding.model: text-embedding-3-small
spring.ai.vectorstore.pgvector.dimensions: 1536       # match the new model
```

**3. Drop the vector_store table** — existing embeddings are incompatible with a different model:

```sql
DROP TABLE IF EXISTS public.vector_store;
```

Restart the server — Spring AI recreates it with the new dimensions.

### What you DON'T need to change

- `IngestionService.java` — uses `VectorStore` interface, doesn't know about embedding models
- `SearchTool.java` / `SearchService.java` — uses `VectorStore.similaritySearch()`, provider-agnostic
- `S3DocumentService.java` — doesn't touch embeddings at all

### Embedding dimensions reference

| Model | Provider | Dimensions | Size |
|---|---|---|---|
| `nomic-embed-text` | Ollama | 768 | 274MB |
| `mxbai-embed-large` | Ollama | 1024 | 670MB |
| `all-minilm` | Ollama | 384 | 45MB |
| `snowflake-arctic-embed` | Ollama | 1024 | 670MB |
| `text-embedding-3-small` | OpenAI | 1536 | — |
| `text-embedding-3-large` | OpenAI | 3072 | — |
| `text-embedding-ada-002` | OpenAI/Azure | 1536 | — |
| `amazon.titan-embed-text-v2` | AWS Bedrock | 1024 | — |

> **pgvector HNSW index limit:** max 2000 dimensions. If your model outputs more than that (e.g., `qwen3-embedding` at 4096), pgvector will reject it. Pick a model that fits.

### Official docs

- [Spring AI Embedding Models](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
- [Ollama Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html)
- [OpenAI Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html)

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

