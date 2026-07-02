package com.helpbot.mcp.dto;

import org.springframework.ai.mcp.annotation.McpToolParam;

import lombok.Data;


@Data
public class HelpDeskTicketResponseDto
{
	@McpToolParam(description = "Auto-generated unique ticket ID")
	Long id;

	@McpToolParam(description = "Description of the issue or support request")
	String details;

	@McpToolParam(description = "Name or identifier of the person who raised the ticket")
	String userId;

	@McpToolParam(description = "Current status of the ticket, e.g. OPEN, IN_PROGRESS, CLOSED")
	String status;

	@McpToolParam(description = "The ID of the related knowledge base document, if applicable")
	String documentId;

	@McpToolParam(description = "Type of ticket — MISSING_INFORMATION or WRONG_INFORMATION")
	TicketType ticketType;
}
