package com.helpbot.mcp.dto;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;


@Data
public class HelpDeskTicketResponseDto
{
	Long id;
	String details;
	String raisedBy;
	String status;
	String documentId;
}
