# Tokenomics

What drives LLM cost (and, on the Ollama branch, compute load) — and the single highest-leverage
thing not yet in place to control it.

## Cost drivers today

Nothing in this codebase currently caps token spend:

- **No `max-tokens` / output cap.** `application.yaml`'s `spring.ai.openai.chat.*` (or
  `spring.ai.ollama.chat.*` on `main_ollama_opensource`) sets only the model name. Compare
  `maitch-search-agent`, which caps `max-tokens: 600` (ADR-A02).
- **Chat memory grows per turn, up to a 20-message window.** Every retained message is re-sent
  as context on every call. See [ARCHITECTURE.md#chat-memory](ARCHITECTURE.md#chat-memory) —
  keyed by username, no compression, no persistence, not shared across instances.
- **Each tool call is a full extra model round trip.** No step limit on the tool-calling loop
  ([ARCHITECTURE.md#loop](ARCHITECTURE.md#loop)) — search + ticket creation = 3+ model calls.
- **No rate limiting.** No equivalent of `maitch-search-agent`'s `RateLimitFilter` on `/chat`.
- **Ingestion re-embeds with no dedup.** 384-token chunks, up to 400/document, re-embedded on
  every run regardless of change (see [ARCHITECTURE.md#ingestion](ARCHITECTURE.md#ingestion)).

Net effect: the same question asked twice, worded differently, pays full price both times. For
a support bot — where most traffic is a handful of intents rephrased ("return policy", "how do
returns work", "can I send this back") — that's the biggest unpulled lever.

## Semantic caching

- Keys a cache on **embedding similarity**, not exact string match — a new question close
  enough (e.g. cosine similarity > 0.85) to a previously-answered one gets the cached answer,
  skipping the model call (and, if placed early, the retrieval/tool loop too).
- **Spring AI 2.0 ships this already** — `SemanticCacheAdvisor` (Redis-backed), same
  `defaultAdvisors(...)` chain `HelpBotChatClientConfig` already uses, placed at the *front*
  (cache → memory → RAG/tools → LLM) so a hit short-circuits before memory, tools, or the model:

```java
// illustrative — not yet wired into HelpBotChatClientConfig
SemanticCacheAdvisor cacheAdvisor = SemanticCacheAdvisor.builder()
        .cache(semanticCache) // backed by a vector store, e.g. Redis
        .build();

chatClientBuilder
        .defaultAdvisors(cacheAdvisor, new SimpleLoggerAdvisor(), new HelpBotTokenCountAdvisor(),
                MessageChatMemoryAdvisor.builder(chatMemory).build())
        ...
```

- Config: `spring.ai.vectorstore.redis.semantic-cache.*` (`enabled`, `similarity-threshold`,
  `index-name`, ...). Redis-backed → adopting it means adding Redis to the stack (not currently
  in `compose.yaml` on either branch), separate from pgvector and any future memory persistence.

**Why it fits here:**
- Support-bot traffic is exactly the "same intent, many phrasings" pattern semantic caching
  targets; exact-string caching wouldn't catch any of it.
- No rate limit or token cap today, so a cache hit is the cheapest available mitigation for a
  spike of near-duplicate questions — no model call, no tool call, no MCP round trip.
- Dollar savings on `main` (OpenAI); latency/compute savings on `main_ollama_opensource`.

**Where it needs care:**
- **Partition by role.** `search_admin` answers can surface internal content; `search` can't. A
  cache shared across `helpBotChatClient`/`helpBotInternalChatClient` without a role-scoped key
  could leak an internal answer to a customer — reopening the boundary
  [AGENTIC-HARNESS.md](AGENTIC-HARNESS.md) documents as two independent layers. Fix: one cache
  namespace per `ChatClient` bean, mirroring the existing tool-list split.
- **TTL vs. the 5-minute ingestion cycle.** Documents can change every 5 minutes; a long-TTL
  cache can serve stale answers. Keep TTL short relative to ingestion cadence, or invalidate on
  `S3IngestionJob`/`POST /api/ingest/all` runs (no hook for that today).
- **Don't swallow ticket creation.** The advisor short-circuits *before* the model picks a
  tool, so it can't know whether an uncached run would've called `search` or
  `createHelpDeskTicket`. Start with a high similarity threshold and scope caching to clearly
  informational traffic, not globally on `/chat`.

Not implemented yet — flagged here so the role-partitioning question isn't skipped by accident
when someone reaches for it.
