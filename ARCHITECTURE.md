# Architecture

A deeper, cross-cutting look at how Helpbot works end to end — pulling together pieces that
each live in their own file/class but only make sense together. For per-module file layouts,
see each module's own README; for the LLM-facing guardrails specifically, see
[AGENTIC-HARNESS.md](AGENTIC-HARNESS.md), which this doc summarizes and links out to.

## RAG

Helpbot is retrieval-augmented generation with a twist: retrieval is exposed as an MCP tool the
model chooses to call, not a step hardcoded into a fixed pipeline.

```
question → ChatClient (LLM decides whether to call a tool) → search / search_admin (MCP tool)
         → SearchService → VectorStore.similaritySearch(topK, minSimilarity, filter)
         → matching chunks → back to the model → model composes the final answer
```

`SearchService` (`helpbot-mcp-server`) is the retrieval core: `searchPublic()` filters on
`internal=false`, `searchAll()` (backing `search_admin`) allows both. `topK` and
`minSimilarity` come from `helpbot.search.*` (`SearchConfig`) — 5 and 0.35 by default. There's
no reranking step and no query rewriting; the raw question text is embedded and matched
directly. The `internal` metadata flag set at ingestion time is the *only* access-control
signal — see the [Agentic Harness](#agentic-harness) section for why that matters.

## Ingestion

```
S3 bucket (public/ or internal/ prefix)
  → S3IngestionJob (@Scheduled every 5 min) or POST /api/ingest/all
  → S3DocumentService.ingestFolder(prefix, internal)   lists + downloads via S3Template
  → IngestionService.chunkAndIngest(resource, internal)
      → TikaDocumentReader        parses PDF/DOCX/PPTX/etc. into plain text
      → TokenTextSplitter         chunk-size 384 tokens, max 400 chunks/doc (helpbot.ingestion.chunk-size)
      → metadata tagging          { internal: bool, source: filename }
      → VectorStore.add()         embeds each chunk, upserts into pgvector
  → source object deleted from S3
```

There's no diffing or dedup — every run re-embeds and re-adds whatever's currently in the
bucket, and S3 is a transient inbox (files are deleted immediately after ingest; the real
source of truth for local dev is `helpbot-mcp-server/localstack/documents/`). This means
re-running ingestion against the same document produces duplicate chunks in pgvector rather
than an update — there's no content-hash check like the sibling `maitch-search` project's
delta-ingestion (`ingestAll(products, firstLoad)`, ADR-030 there). Worth knowing if you're
testing repeated ingestion locally.

## MCP

`helpbot-mcp-server` is the MCP server, exposing 4 tools over Streamable HTTP at `/mcp`:
`search`, `search_admin`, `createHelpDeskTicket`, `getHelpDeskTicketsByUserId`. Tool names are
explicit on `@McpTool` because Spring AI's MCP server support otherwise derives camelCase names
from the method.

`helpbot-agent` is the MCP *client*, connecting via `McpSyncClient` over Streamable HTTP
(`spring.ai.mcp.client.streamable-http.connections.helpbot-mcp-server.url`). This connection is
made **eagerly and synchronously at application context startup** — if the server isn't
reachable, the agent fails to start entirely (a known upstream limitation, not something
configurable here — see [spring-ai#3232](https://github.com/spring-projects/spring-ai/issues/3232)).
That's why CI builds the agent module without running its `@SpringBootTest` — see `CLAUDE.md`.

There's no authentication on `/mcp` at all — anything that can reach the port can call any
tool. This is fine for local dev; it's the first thing to add before this MCP server is
reachable from anywhere untrusted.

## Agent

`helpbot-agent` is a thin routing layer in front of the LLM:

```
GET /chat?question=... (Basic Auth)
  → HelpBotChatController → HelpBotService.chat()
  → role check (SecurityContextHolder) → one of two ChatClient beans
      helpBotChatClient          (CUSTOMER)  tools: search, createHelpDeskTicket, getHelpDeskTicketsByUserId
      helpBotInternalChatClient  (EMPLOYEE)  tools: search_admin, createHelpDeskTicket, getHelpDeskTicketsByUserId
  → LLM call(s), see Loop below
  → plain-text answer
```

Both `ChatClient` beans are built once at startup in `HelpBotChatClientConfig`, each pre-filtered
to its tool allow-list via `ToolsUtil.selectToolsFor()`. There's exactly one HTTP endpoint —
no separate "continue conversation" endpoint; every call to `/chat` (first message or the
tenth) goes through the same path, with continuity provided entirely by chat memory keyed on
username.

## Loop

Unlike the sibling `maitch-search-agent` repo — which hand-writes its agent loop in Java with
explicit circuit breakers (`max-clarification-turns`, `max-steps` in `SearchAgentService`) — the
tool-calling loop here is entirely framework-managed. `ChatClient.defaultTools(...)` hands
Spring AI the tool list, and Spring AI internally repeats *call model → if it requested a tool,
execute it and feed the result back → call model again* until the model responds with plain
text instead of a tool call. There is no application-level step limit, turn cap, or timeout on
this loop in `helpbot-agent` — it relies entirely on Spring AI's internal defaults (and, at the
tool level, the 10s-ish network timeout inherent to each MCP round trip). A model stuck
alternating between `search` and `getHelpDeskTicketsByUserId` has nothing in this codebase
stopping it early.

## Chat Memory

Cross-turn continuity is entirely `MessageChatMemoryAdvisor`'s job — there's no explicit
session object anywhere in `helpbot-agent`. A few specifics worth knowing:

- **Keyed by username, not by a client-supplied conversation ID.** `HelpBotService.chat()` binds
  `CONVERSATION_ID = getUserName()` on every call. There's no way for one authenticated user to
  hold two parallel conversations, and no way for the caller to start a fresh one short of a
  different user logging in — `/chat` has no "reset" or "new conversation" affordance.
- **Tool calls don't inflate the memory window.** `MessageChatMemoryAdvisor` is added at its
  default position (`HelpBotChatClientConfig` never overrides `.order(...)`), which means it
  loads history once *before* the tool-calling loop starts and, per Spring AI's documented
  default behavior, persists only the final user question and final assistant answer for that
  turn — not the intermediate tool-call/tool-response messages. So a question that triggers two
  tool calls costs more tokens *for that one request* (see [Tokenomics](#tokenomics)) but only
  adds 2 messages to memory, same as a question that needed no tools at all.
- **20-message sliding window ≈ 10 turns.** The autoconfigured `ChatMemory` bean is
  `MessageWindowChatMemory` with Spring AI's default max size of 20 messages (10 user + 10
  assistant, given the point above). Once full, the oldest messages are evicted — there's no
  summarization step like `maitch-search-agent`'s `Customer: … / Agent asked: …` compression,
  so context just falls off a cliff at message 21 rather than degrading gracefully.
- **In-memory, unbounded by time, not shared across instances.** Backed by Spring AI's default
  `InMemoryChatMemoryRepository` — no JDBC/Redis chat-memory starter is on the classpath. Memory
  is lost on restart, isn't visible to a second `helpbot-agent` instance if you scale out, and
  never expires by age — only the 20-message window bounds it, so an idle user's history from
  months ago is still there verbatim on their next message, right up until the window pushes it
  out.

## Agentic Harness

See [AGENTIC-HARNESS.md](AGENTIC-HARNESS.md) for the full writeup. Summary: tool allow-lists
(`ToolsUtil`) and the server-side `internal` filter (`SearchService`) are **harness-enforced** —
code-level, testable, survive prompt edits. The system prompt (`helpbot-system.st`) and user
template (`user-system.st`) are **model-facing instructions** — the model is expected to comply,
but nothing else checks that it did. The doc calls out one concrete gap: the
"wrong-information tickets are employee-only" rule exists only as prompt text, with no
equivalent tool-level check the way `search`/`search_admin` has one.

## Tokenomics

Nothing in this codebase currently caps token spend — worth knowing before pointing it at
production traffic:

- **No `max-tokens` / output cap configured.** `application.yaml`'s `spring.ai.openai.chat.*`
  sets only the model name (`gpt-4o-mini`); there's no ceiling on response length. Compare to
  `maitch-search-agent`, which caps `max-tokens: 600` explicitly (ADR-A02 there).
- **Chat memory grows per turn, up to the 20-message window.** Every retained message gets
  re-sent as context on every subsequent call, so a long conversation's per-call token cost
  climbs until the window caps it. See [Chat Memory](#chat-memory) for the mechanism and its
  gaps (no compression, no persistence, no cross-instance sharing).
- **Each tool call is a full extra round trip.** The [Loop](#loop) above means a question that
  requires, say, a search followed by a ticket creation costs at least 3 model calls (initial +
  after search result + after ticket-creation result), each carrying the accumulated
  conversation and tool results as input tokens.
- **No rate limiting.** There's no equivalent of `maitch-search-agent`'s `RateLimitFilter` on
  `/chat` — nothing bounds request volume per caller beyond whatever Basic Auth's two demo
  users naturally limit you to locally.
- **Ingestion-side cost** scales with chunk count: 384-token chunks, up to 400 chunks per
  document (`helpbot.ingestion.chunk-size`), each embedded once per ingestion run — and since
  ingestion has no dedup (see [Ingestion](#ingestion)), re-running it against unchanged
  documents re-embeds them for no benefit.

## Testing (to be added)

There is currently no automated test coverage for response *quality* — only
`@SpringBootTest` context-loads smoke tests in both modules (see `CLAUDE.md`). Nothing checks
whether `search` results are actually relevant to a question, or whether the model's final
answer is grounded in the retrieved chunks rather than invented.

The plan is to close that gap using **Spring AI's evaluation framework**
(`org.springframework.ai.chat.client.evaluation` — `RelevancyEvaluator` and
`FactCheckingEvaluator`), which uses a judge LLM call to score a response against the
question and/or the retrieved context. Shape of the intended test:

1. A fixed set of golden questions per role (public-only vs. public+internal).
2. Drive them through the real `/chat` endpoint (or the `ChatClient` beans directly).
3. Feed `{question, retrieved context, answer}` into `RelevancyEvaluator` (does the answer
   address the question?) and `FactCheckingEvaluator` (is the answer supported by the
   retrieved chunks, or hallucinated?).
4. Fail the build below a relevancy/groundedness threshold, the same way `jacocoTestCoverageVerification`
   gates on coverage today.

Not implemented yet — flagging the shape here so it isn't designed from scratch later.
