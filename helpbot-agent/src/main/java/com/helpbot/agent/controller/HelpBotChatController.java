package com.helpbot.agent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpbot.agent.service.HelpBotService;


@RestController
@RequestMapping("/chat")
@Tag(name = "Chat", description = "Chat endpoints for querying the helpbot knowledge base")
@AllArgsConstructor
public class HelpBotChatController
{
	HelpBotService helpBotService;

	@GetMapping
	@Operation(summary = "Chat", description = "Routes to the right search tool based on user role. "
			+ "EMPLOYEE role searches all documents (public + internal). "
			+ "CUSTOMER role searches public documents only.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Successful response"),
			@ApiResponse(responseCode = "401", description = "Unauthorized — basic auth required"),
			@ApiResponse(responseCode = "403", description = "Forbidden — insufficient role")
	})
	public String chat(@RequestParam("question") String question)
	{
		return helpBotService.chat(question);
	}
}
