package com.helpbot.agent.ai;

import java.util.List;

import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;

import io.modelcontextprotocol.client.McpSyncClient;


public class ToolsUtil
{

	/**
	 * Keeps only the tools whose MCP server name and tool name match the given hints. A null/blank hint means "match everything"
	 */
	public static ToolCallback[] selectToolsFor(List<McpSyncClient> mcpClients,
			String serverName, List<String> toolNames)
	{
		return mcpClients.stream()
				.flatMap(client -> client.listTools().tools().stream()
						// getServerInfo().name() is the same value the global filter reads
						.filter(tool -> matches(client.getServerInfo().name(), serverName)
								&& toolNames.contains(tool.name()))
						.map(tool -> (ToolCallback) SyncMcpToolCallback.builder()
								.mcpClient(client)
								.tool(tool)
								.build()))
				.toArray(ToolCallback[]::new);
	}

	private static boolean matches(String actual, String hint)
	{
		return hint == null || hint.isBlank()
				|| actual.toLowerCase().contains(hint.toLowerCase());
	}
}
