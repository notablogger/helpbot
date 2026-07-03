package com.helpbot.agent.service;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
public class HelpBotService
{
	ChatClient helpBotChatClient;
	ChatClient helpBotInternalChatClient;
	Resource userPrompt;

	HelpBotService(ChatClient helpBotChatClient, ChatClient helpBotInternalChatClient,
			@Value("classpath:/prompts/user-system.st") Resource userPrompt)
	{
		this.helpBotChatClient = helpBotChatClient;
		this.helpBotInternalChatClient = helpBotInternalChatClient;
		this.userPrompt = userPrompt;
	}

	public String chat(final String question)
	{
		if (isEmployee())
		{
			log.info("Employee role detected, using internal chat client for question: {}", question);
			return helpBotInternalChatClient.prompt()
					// binds {userName} in the default system prompt - defaultSystem() only sets the
					// text, so without this the system message keeps the literal "{userName}" placeholder
					.system(sys -> sys.param("userName", getUserName()))
					//prompt stuffing user message
					.user(getPromptUserSpecConsumer(question))
					.advisors(
							advisorSpec -> advisorSpec.param(CONVERSATION_ID, getUserName())
					).call().content();
		}
		log.info("Customer role detected, using public chat client for question: {}", question);
		return helpBotChatClient.prompt()
				.system(sys -> sys.param("userName", getUserName()))
				//prompt stuffing user message
				.user(getPromptUserSpecConsumer(question)).advisors(
						advisorSpec -> advisorSpec.param(CONVERSATION_ID, getUserName())
				).call().content();
	}

	private @NonNull Consumer<ChatClient.PromptUserSpec> getPromptUserSpecConsumer(final String question)
	{
		return promptTemplateSpec -> promptTemplateSpec.text(userPrompt).param("userName", getUserName())
				.param("question", question).param("role", isEmployee() ? "employee" : "customer");
	}

	private boolean isEmployee()
	{
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"));
	}

	private String getUserName()
	{
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null ? auth.getName() : "unknown";
	}
}
