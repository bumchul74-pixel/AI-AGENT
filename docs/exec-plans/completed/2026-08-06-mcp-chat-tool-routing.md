# MCP chat explicit tool routing

## Goal

Route every explicitly named AI-MCP tool from the chat screen to `tools/call` instead of incorrectly returning `tools/list` or server metadata.

## Constraints

- Preserve the AI-MCP gateway boundary and existing MCP transport.
- Invoke only the audited AI-MCP tool allowlist.
- Validate required arguments before calling a tool.
- Keep tool results as LLM grounding context and report the actual operation as the MCP reference.
- Do not expose credentials, complete prompts, or sensitive payloads in logs.

## Non-goals

- No new public REST endpoint.
- No change to MCP server implementations.
- No autonomous selection of destructive tools when the user did not explicitly name them.

## Outcome

All 17 tools in the live AI-MCP `tools/list` inventory are allowlisted for explicit name routing. Required arguments are validated before `tools/call`, natural-language shortcuts remain for table and rule lists, and MCP references use the actual operation. The `get_server_info` argument name now matches the live schema (`detailLevel`).

## Steps

1. Record the live AI-MCP tool names and required input fields.
2. Add allowlisted explicit-tool resolution and tool-specific argument extraction/validation.
3. Keep natural-language shortcuts for existing supported read operations.
4. Correct tool input names that differ from the live schema.
5. Add regression coverage for no-argument and required-argument tools, missing arguments, and MCP reference labels.
6. Run targeted tests and `verifyAll`.

## Verification

- `AiMcpChatContextProviderTest`
- `AiMcpGatewayServiceTest`
- `ChatServiceImplTest`
- `.\gradlew.bat verifyAll --console=plain`

## Risks

- Natural-language argument extraction can be ambiguous.
- Base64 or source payloads can be large.
- Tool inventories may evolve independently of the allowlist.

## Rollback

Revert the provider, gateway, tests, and this execution plan.

## Decisions

- Exact tool-name invocation is required for tools with side effects or required arguments.
- Missing required arguments produce a validation error instead of silently calling another MCP operation.
- The allowlist is based on the live `tools/list` inventory observed on 2026-08-06.
