package com.helpbot.mcp.tools;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import com.helpbot.mcp.dto.HelpDeskTicketRequestDto;
import com.helpbot.mcp.dto.HelpDeskTicketResponseDto;
import com.helpbot.mcp.service.HelpDeskTicketService;

import lombok.AllArgsConstructor;


/**
 * MCP tools for managing help desk tickets.
 * <p>
 * Exposes tools to create tickets and query them by document ID.
 * Tickets are stored in PostgreSQL via JPA.
 */
@Service
@AllArgsConstructor
public class HelpDeskTicketTool
{
	HelpDeskTicketService helpDeskTicketService;

	/**
	 * Creates a new help desk ticket.
	 *
	 * @param requestDto ticket details including description, raised by, status, and document ID
	 * @return the created ticket with its generated ID
	 */
	@McpTool(name = "createHelpDeskTicket", description = "Create a new Help Desk Ticket. Use this when a user wants to raise a support request or report an issue.")
	HelpDeskTicketResponseDto createHelpDeskTicket(
			@McpToolParam(description = "The help desk ticket to create, including details, raisedBy, status, and documentId fields") HelpDeskTicketRequestDto requestDto)
	{
		return helpDeskTicketService.createHelpDeskTicket(requestDto);
	}

	/**
	 * Retrieves all help desk tickets associated with a specific document.
	 *
	 * @param documentId the document ID to look up tickets for
	 * @return list of tickets linked to the given document
	 */
	@McpTool(name = "getHelpDeskTicketsByDocumentId", description = "Get all Help Desk Tickets linked to a specific document. Use this to check existing issues or requests related to a document.")
	List<HelpDeskTicketResponseDto> getHelpDeskTicketsByDocumentId(
			@McpToolParam(description = "The document ID to search tickets for") String documentId)
	{
		return helpDeskTicketService.findAllHelpDeskTicket(documentId);
	}
}
