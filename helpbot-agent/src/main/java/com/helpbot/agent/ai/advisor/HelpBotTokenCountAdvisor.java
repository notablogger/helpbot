package com.helpbot.agent.ai.advisor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import lombok.extern.slf4j.Slf4j;


/*
 * This class is responsible to log token count for each call
 * It can be hooked to any chat client and will be called as part of advisors chain.
 */
@Slf4j
public class HelpBotTokenCountAdvisor implements CallAdvisor
{


	@Override
	public ChatClientResponse adviseCall(final @NonNull ChatClientRequest chatClientRequest,
			final CallAdvisorChain callAdvisorChain)
	{
		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
		ChatResponse chatResponse = chatClientResponse.chatResponse();
		assert chatResponse != null;
		Usage usage = chatResponse.getMetadata().getUsage();
		log.info("Token usage details : {}", usage.toString());
		return chatClientResponse;
	}


	@Override
	public String getName()
	{
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder()
	{
		return 0;
	}

}
