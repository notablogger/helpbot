package com.helpbot.mcp.tools;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import com.helpbot.mcp.dto.HelpDeskTicketRequestDto;
import com.helpbot.mcp.dto.HelpDeskTicketResponseDto;
import com.helpbot.mcp.service.HelpDeskTicketService;

import lombok.AllArgsConstructor;


@Service
@AllArgsConstructor
public class HelpDeskTicketTool
{
	HelpDeskTicketService helpDeskTicketService;

	@McpTool(name = "createHelpDeskTicket", description = "Create a new Help Desk Ticket.")
	HelpDeskTicketResponseDto createHelpDeskTicket(@McpToolParam HelpDeskTicketRequestDto requestDto)
	{
		return helpDeskTicketService.createHelpDeskTicket(requestDto);
	}

	@McpTool(name = "getHelpDeskTicketsByDocumentId", description = "Get Help Desk Tickets by Document Id.")
	List<HelpDeskTicketResponseDto> getHelpDeskTicketsByDocumentId(
			@McpToolParam(description = "The document ID to search for.") String documentId)
	{
		return helpDeskTicketService.findAllHelpDeskTicket(documentId);
	}
}
