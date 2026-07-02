package com.helpbot.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@AllArgsConstructor
@Slf4j
public class HelpBotService
{
	ChatClient helpBotChatClient;
	ChatClient helpBotInternalChatClient;

	public String chat(final String question)
	{
		if (isEmployee())
		{
			log.info("Employee role detected, using internal chat client for question: {}", question);
			return helpBotInternalChatClient.prompt().user(question).call().content();
		}
		log.info("Customer role detected, using public chat client for question: {}", question);
		return helpBotChatClient.prompt().user(question).call().content();
	}

	private boolean isEmployee()
	{
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"));
	}
}
