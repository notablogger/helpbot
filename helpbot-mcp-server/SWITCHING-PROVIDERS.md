# Switching Providers

One of the nice things about Spring AI is that it abstracts away the actual providers behind interfaces. Your code talks to `VectorStore` and `EmbeddingModel` — it doesn't care if the embeddings come from Ollama, OpenAI, or anything else. Same for the vector store.

So switching providers is mostly a matter of swapping a dependency in `build.gradle` and updating `application.yaml`. No code changes needed in most cases.

This doc walks through how to swap each component.

---

## Switching the embedding model provider

Right now we use Ollama with `embeddinggemma:latest`. The code never directly touches the embedding model — Spring AI's `VectorStore` calls it internally when you do `vectorStore.add()`.

### What to change

**1. build.gradle** — swap the model starter:

```groovy
// Current: Ollama
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'

// Option: OpenAI
implementation 'org.springframework.ai:spring-ai-starter-model-openai'

// Option: Azure OpenAI
implementation 'org.springframework.ai:spring-ai-starter-model-azure-openai'

// Option: AWS Bedrock (Titan, Cohere)
implementation 'org.springframework.ai:spring-ai-starter-model-bedrock'

// Option: Google Vertex AI
implementation 'org.springframework.ai:spring-ai-starter-model-vertex-ai'

// Option: Mistral AI
implementation 'org.springframework.ai:spring-ai-starter-model-mistral-ai'
```

**2. application.yaml** — update the config for the new provider:

```yaml
# Current: Ollama
spring.ai.ollama.embedding.model: embeddinggemma:latest

# OpenAI example
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.embedding.model: text-embedding-3-small

# Azure OpenAI example
spring.ai.azure.openai.api-key: ${AZURE_OPENAI_API_KEY}
spring.ai.azure.openai.endpoint: https://your-resource.openai.azure.com
spring.ai.azure.openai.embedding.deployment-name: text-embedding-ada-002
```

**3. Update dimensions** — different models output different vector sizes. You MUST update this to match:

```yaml
spring.ai.vectorstore.pgvector.dimensions: <new-dimension>
```

Common embedding dimensions:

| Model | Provider | Dimensions |
|---|---|---|
| `embeddinggemma:latest` | Ollama | 768 |
| `nomic-embed-text` | Ollama | 768 |
| `mxbai-embed-large` | Ollama | 1024 |
| `all-minilm` | Ollama | 384 |
| `text-embedding-3-small` | OpenAI | 1536 |
| `text-embedding-3-large` | OpenAI | 3072 |
| `text-embedding-ada-002` | OpenAI/Azure | 1536 |
| `amazon.titan-embed-text-v2` | AWS Bedrock | 1024 |

**4. Drop the vector_store table** — the existing embeddings were generated with the old model's dimensions. They're incompatible with the new model. Drop and re-ingest:

```sql
DROP TABLE IF EXISTS public.vector_store;
```

Then restart the server — Spring AI will recreate it with the new dimensions.

### What you DON'T need to change

- `IngestionService.java` — uses `VectorStore` interface, doesn't know about embedding models
- `SearchTool.java` — uses `VectorStore.similaritySearch()`, provider-agnostic
- `S3DocumentService.java` — doesn't touch embeddings at all

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

// Option: Chroma
implementation 'org.springframework.ai:spring-ai-starter-vector-store-chroma'

// Option: Milvus
implementation 'org.springframework.ai:spring-ai-starter-vector-store-milvus'

// Option: Pinecone
implementation 'org.springframework.ai:spring-ai-starter-vector-store-pinecone'

// Option: Weaviate
implementation 'org.springframework.ai:spring-ai-starter-vector-store-weaviate'

// Option: Redis
implementation 'org.springframework.ai:spring-ai-starter-vector-store-redis'

// Option: Elasticsearch
implementation 'org.springframework.ai:spring-ai-starter-vector-store-elasticsearch'

// Option: Neo4j
implementation 'org.springframework.ai:spring-ai-starter-vector-store-neo4j'
```

**2. application.yaml** — replace the pgvector config with the new provider's config:

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

**3. compose.yaml** — if the new vector store runs in Docker, add it. If it's a managed service (Pinecone, etc.), you can remove the pgvector service entirely.

### What you DON'T need to change

- `IngestionService.java` — calls `vectorStore.add()`, works with any implementation
- `SearchTool.java` — calls `vectorStore.similaritySearch()`, same interface everywhere
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
- [Milvus](https://docs.spring.io/spring-ai/reference/api/vectordbs/milvus.html)

---

## Switching the Ollama model (staying on Ollama)

If you just want a different model but still want to run locally via Ollama, it's even simpler.

**1. compose.yaml** — change the model pull command:

```yaml
ollama-model-pull:
  command: ["pull", "nomic-embed-text:latest"]  # was embeddinggemma:latest
```

**2. application.yaml** — update the model name and dimensions:

```yaml
spring.ai.ollama.embedding.model: nomic-embed-text:latest
spring.ai.vectorstore.pgvector.dimensions: 768  # check the model's output size
```

**3. Drop and re-ingest** (if dimensions changed):

```sql
DROP TABLE IF EXISTS public.vector_store;
```

That's it. Restart the server.

### Popular Ollama embedding models

| Model | Dimensions | Size | Notes |
|---|---|---|---|
| `nomic-embed-text` | 768 | 274MB | Good general purpose |
| `mxbai-embed-large` | 1024 | 670MB | Higher quality, bigger |
| `all-minilm` | 384 | 45MB | Tiny and fast |
| `snowflake-arctic-embed` | 1024 | 670MB | Strong retrieval performance |
| `embeddinggemma` | 768 | 1.6GB | Google's, what we use now |

Browse all models: [Ollama Embedding Models](https://ollama.com/search?c=embedding)

---

## Why this works without code changes

Spring AI is designed around abstractions:

```
Your code
  → VectorStore interface        (add, similaritySearch, delete)
  → EmbeddingModel interface     (embed text into vectors)

Spring Boot auto-configuration
  → picks the right implementation based on which starter is on the classpath
  → configures it from application.yaml
```

So `IngestionService` and `SearchTool` talk to `VectorStore` — they never import `PgVectorStore` or `OllamaEmbeddingModel` directly. That's why swapping is just a dependency + config change.

The only thing you need to be careful about: **dimensions must match between the embedding model and the vector store**. If they don't, you'll get errors like `expected 384 dimensions, not 768`.

---

## Quick reference: what to change where

| Switching... | build.gradle | application.yaml | compose.yaml | Java code | Re-ingest? |
|---|---|---|---|---|---|
| Embedding provider (e.g., Ollama → OpenAI) | swap model starter | update model config + dimensions | remove ollama services | no changes | yes |
| Ollama model (e.g., embeddinggemma → nomic) | no changes | update model name + dimensions | update pull command | no changes | yes |
| Vector store (e.g., pgvector → Pinecone) | swap vector store starter | update vector store config | swap/remove DB service | no changes | yes |
| Document storage (e.g., S3 → GCS) | swap cloud starter | update cloud config | swap LocalStack | update S3DocumentService | no |

