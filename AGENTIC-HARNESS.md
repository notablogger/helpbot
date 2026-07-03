# Agentic Harness

The *harness* around the LLM in `helpbot-agent` — code/prompt pieces that shape what the model
can do, vs. what the model itself decides. Includes one known gap: a rule enforced by prompt
text alone.

## The layers

todo - to be simplified


| Layer | File | Controls |
|---|---|---|
| Tool allow-list per role | `HelpBotChatClientConfig` + `ToolsUtil.selectToolsFor()` | Which MCP tools a `ChatClient` bean can see — hardcoded `List<String>` per role, set at bean-construction time |
| Server-side data filter | `SearchService.searchPublic()` / `searchAll()` (`helpbot-mcp-server`) | Which documents a search can return, filtered on `internal` chunk metadata |
| System prompt | `prompts/helpbot-system.st` | Tone, ticket creation, greeting, ticket-type policy — identical text for both roles |
| User prompt template | `prompts/user-system.st` | Wraps each question with `{userName}`, `{role}`, `{question}` |
| Chat memory | `MessageChatMemoryAdvisor`, keyed by username | Prior-turn visibility (e.g. detecting first reply) |
| Token logging | `HelpBotTokenCountAdvisor` | Observability only, not a guardrail |

- **Harness-enforced** (first two rows): Java/config, runs regardless of model cooperation.
  `search`/`search_admin` has *two* independent layers (tool-list + server-side filter) — see
  `CLAUDE.md`'s "defense in depth" note.
- **Model-facing instructions** (prompt rows): the model is expected to comply; nothing checks
  that it did.

## Known gap: ticket-type policy is prompt-only

`helpbot-system.st` rule 6: "wrong information" tickets should only be raised on behalf of an
employee; "need more information" tickets can be raised by anyone.

- Both chat clients expose the same `createHelpDeskTicket` tool with the same `ticketType`
  param — no code (`HelpDeskTicketTool`, `HelpDeskTicketService`, `HelpDeskTicketRequestDto`)
  checks who's calling.
- The MCP server has no request-level auth at all, so there's nothing to check against even if
  it wanted to.

**Why that's not good practice:**

- A system prompt is a request, not a constraint — a confused conversation or an adversarial
  "ignore earlier instructions" attempt can produce a tool call that ignores it, and the tool
  honors it regardless.
- Degrades silently — if `helpbot-system.st` is reworded/trimmed and rule 6 weakens or drops,
  nothing else notices.
- Competes for attention — one instruction among several in a prompt shared by both roles,
  rather than structurally impossible to violate (like an absent tool).

**Real enforcement would need:** the caller's role to travel from the agent
(`SecurityContextHolder`) to the MCP server (which doesn't receive it today) — e.g. a header
`X-User-Role`, and the `helpbot-server` validates before honoring
`ticketType=WRONG_INFORMATION`. Cross-repo, cross-process change, out of scope for a prompt fix
— documented here so the gap is visible rather than silently assumed handled.
