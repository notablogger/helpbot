# Tokenomics

What actually drives LLM cost (and, on the Ollama branch, compute load) in this app, and the
single highest-leverage thing not yet in place to bring it under control.

## Cost drivers today

Nothing in this codebase currently caps token spend:

- **No `max-tokens` / output cap configured.** `application.yaml`'s `spring.ai.openai.chat.*`
  (or `spring.ai.ollama.chat.*` on `main_ollama_opensource`) sets only the model name; there's
  no ceiling on response length. Compare to the sibling `maitch-search-agent` repo, which caps
  `max-tokens: 600` explicitly (ADR-A02 there).
- **Chat memory grows per turn, up to a 20-message window.** Every retained message is
  re-sent as context on every subsequent call, so a long conversation's per-call token cost
  climbs until the window caps it at ~10 turns. See
  [ARCHITECTURE.md#chat-memory](ARCHITECTURE.md#chat-memory) for the full mechanism — keyed by
  username, no compression, no persistence, not shared across instances.
- **Each tool call is a full extra model round trip.** The framework-managed tool-calling loop
  (see [ARCHITECTURE.md#loop](ARCHITECTURE.md#loop)) has no step limit. A question that needs a
  search followed by a ticket creation costs at least 3 model calls, each carrying the
  accumulated conversation and tool results as input tokens.
- **No rate limiting.** There's no equivalent of `maitch-search-agent`'s `RateLimitFilter` on
  `/chat` — nothing bounds request volume per caller.
- **Ingestion-side cost scales with chunk count, with no dedup.** 384-token chunks, up to 400
  per document, each embedded on every ingestion run — re-running ingestion against unchanged
  documents re-embeds them for no benefit (see
  [ARCHITECTURE.md#ingestion](ARCHITECTURE.md#ingestion)).

Put together: the same question asked twice, worded differently, pays full price both times —
full retrieval, full model call, full tool loop if one triggers. For a support bot, where a
large fraction of real traffic is the same handful of questions rephrased ("what's your return
policy", "how do I return something", "can I send this back") — that's the single biggest lever
not yet pulled.

## Semantic caching

Semantic caching means keying a cache not on the exact question string, but on **embedding
similarity** — a new question that's close enough (cosine similarity above some threshold, e.g.
0.85) to a previously-answered one gets served the cached answer, skipping the model call (and,
if the cache sits early enough, the retrieval/tool loop too).

**Spring AI 2.0 (which this repo already runs) ships this out of the box**, not as something to
build from scratch: `SemanticCacheAdvisor`, part of a Redis-backed cache module, plugs into the
same `ChatClient.Builder.defaultAdvisors(...)` chain `HelpBotChatClientConfig` already uses. It
sits at the *front* of the advisor chain — cache → memory → RAG/tools → LLM — and can
short-circuit the whole request if it finds a close-enough match, before `MessageChatMemoryAdvisor`,
before any tool call, before the model is invoked at all:

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

Configuration is property-driven (`spring.ai.vectorstore.redis.semantic-cache.*`: `enabled`,
`similarity-threshold`, `index-name`, ...). The shipped implementation is Redis-backed, which
means adopting it means adding Redis to the stack — not currently in `compose.yaml` on either
branch — purely as a cache store, separate from pgvector (the document vector store) and
separate from any future chat-memory persistence.

### Why this is a good fit here specifically

- The workload is exactly what semantic caching is for: a support bot fields the same small set
  of intents ("refund policy", "business hours", "how do I contact support") phrased a hundred
  different ways. Exact-string caching wouldn't catch any of that; embedding-similarity caching
  is built for it.
- There's no rate limiting or token cap today (see above), so a spike of near-duplicate
  questions currently costs full price every time with nothing to blunt it. A cache hit is the
  cheapest possible mitigation — no model call, no tool call, no MCP round trip at all.
- On `main` (OpenAI), that's a direct dollar saving per cache hit. On `main_ollama_opensource`,
  it's a latency/compute saving instead (no API metering to save on), still meaningful if
  traffic volume is nontrivial.

### Where it needs care, specific to this app

- **Cache entries must be partitioned by role.** `search_admin` answers can surface internal
  document content; `search` answers can't. If a semantic cache is shared across the
  `helpBotChatClient` (CUSTOMER) and `helpBotInternalChatClient` (EMPLOYEE) beans without a
  role-scoped cache key or index, a cached employee answer could leak internal information to a
  customer asking a similarly-worded question — silently reopening the boundary
  [AGENTIC-HARNESS.md](AGENTIC-HARNESS.md) documents `search`/`search_admin` enforcing in two
  independent layers. The straightforward fix is one cache namespace/index per `ChatClient`
  bean (i.e., per role), matching how tool allow-lists are already split.
- **Staleness vs. the 5-minute ingestion cycle.** Documents get re-ingested (and can change)
  every 5 minutes. A semantic cache with a long or no TTL can serve an answer based on content
  that's since been updated or removed. The cache TTL needs to be short relative to the
  ingestion cadence, or explicitly invalidated when `S3IngestionJob`/`POST /api/ingest/all` runs
  — there's no hook for the latter today.
- **Don't let it swallow ticket creation.** The advisor short-circuits *before* the model would
  have decided which tool to call — it can't yet know whether an uncached run of this question
  would have called `search` or `createHelpDeskTicket`. A phrasing close to a cached
  informational answer ("how do I return a broken item") could plausibly mask what was actually
  a "I want to report this as broken, please log it" ticket request. Start with a conservative
  (high) similarity threshold, and treat this as the reason to keep caching scoped to clearly
  informational, read-only traffic rather than turning it on globally for `/chat`.

Not implemented yet in either module — this section exists so the tradeoffs are visible before
someone reaches for it, and so "just cache it" doesn't skip the role-partitioning question by
accident.
