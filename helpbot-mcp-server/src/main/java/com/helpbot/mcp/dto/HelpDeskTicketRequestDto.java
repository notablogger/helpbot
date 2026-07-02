package com.helpbot.mcp.dto;

import lombok.Data;


@Data
public class HelpDeskTicketRequestDto
{
	String details;
	String raisedBy;
	String status;
	String documentId;
}
