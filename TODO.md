# TODO

Not implemented yet. Tracked here so the next pass doesn't start from scratch.

1. **Add semantic caching.** Spring AI 2.0's `SemanticCacheAdvisor` (Redis-backed) slots into
   the existing `defaultAdvisors(...)` chain ahead of memory/RAG/tools. See
   [TOKENOMICS.md#semantic-caching](TOKENOMICS.md#semantic-caching) for the shape, and the care
   it needs specific to this app: cache entries must be partitioned by role or a cached
   `search_admin` answer can leak internal content to a customer, and TTL needs to stay short
   relative to the 5-minute ingestion cycle.
2. **Add role-based access for MCP tools.** Today `helpbot-mcp-server` has no request-level
   auth at all, so the "wrong-information tickets are employee-only" rule in
   `helpbot-system.st` is enforced by prompt text alone — see
   [AGENTIC-HARNESS.md](AGENTIC-HARNESS.md#known-gap-ticket-type-policy-is-prompt-only) for why
   that's not good practice. Needs the caller's role to travel from `helpbot-agent`
   (`SecurityContextHolder`) to the MCP server (e.g. a header), validated in
   `HelpDeskTicketTool`/`HelpDeskTicketService` before honoring `ticketType=WRONG_INFORMATION`.
3. **Introduce query sanitization.** The raw question string flows straight from `/chat` into
   the system/user prompt templates and into `VectorStore.similaritySearch()` — no
   normalization, length cap, or prompt-injection filtering before it reaches the model or the
   embedding call.
4.
