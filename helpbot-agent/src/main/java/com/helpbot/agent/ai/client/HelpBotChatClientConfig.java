package com.helpbot.agent.ai.client;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.ai.chat.client.ChatClient.Builder;

import com.helpbot.agent.ai.ToolsUtil;
import com.helpbot.agent.ai.advisor.HelpBotTokenCountAdvisor;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.AllArgsConstructor;


@Configuration
@AllArgsConstructor
public class HelpBotChatClientConfig
{

	private final List<McpSyncClient> mcpClients;

	@Bean
	public ChatClient helpBotChatClient(Builder chatClientBuilder,
			ToolCallbackProvider mcpTools,
			@Value("classpath:/prompts/helpbot-system.st") Resource systemPrompt)
	{
		ToolCallback[] toolCallbacks = ToolsUtil.selectToolsFor(mcpClients, null,
				Arrays.asList("createHelpDeskTicket", "getHelpDeskTicketsByDocumentId", "search"));

		return chatClientBuilder
				// Give the model instructions for how to work the helpbot,
				.defaultSystem(sys -> sys.text(systemPrompt)
				).defaultAdvisors(new SimpleLoggerAdvisor(), new HelpBotTokenCountAdvisor())
				// Expose limited tool the MCP server publishes. Spring AI auto-executes
				// these in a loop, so the LLM can take as many steps as it needs.
				.defaultTools(toolCallbacks)
				.build();
	}

	@Bean
	public ChatClient helpBotInternalChatClient(Builder chatClientBuilder,
			@Value("classpath:/prompts/helpbot-system.st") Resource systemPrompt)
	{
		ToolCallback[] toolCallbacks = ToolsUtil.selectToolsFor(mcpClients, null,
				Arrays.asList("createHelpDeskTicket", "search_admin", "getHelpDeskTicketsByDocumentId"));

		return chatClientBuilder
				// Give the model instructions for how to work the helpbot,
				.defaultSystem(sys -> sys.text(systemPrompt)
				).defaultAdvisors(new SimpleLoggerAdvisor(), new HelpBotTokenCountAdvisor())
				// Expose limited tool the MCP server publishes. Spring AI auto-executes
				// these in a loop, so the LLM can take as many steps as it needs.
				.defaultTools(toolCallbacks)
				.build();
	}
}
