package com.helpbot.mcp.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.helpbot.mcp.dto.HelpDeskTicketRequestDto;
import com.helpbot.mcp.dto.HelpDeskTicketResponseDto;
import com.helpbot.mcp.rds.entity.HelpDeskTicket;


@Mapper(componentModel = "spring")
public interface HelpDeskTicketMapper
{
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "raisedBy", source = "userId")
	HelpDeskTicket toHelpDeskTicket(HelpDeskTicketRequestDto helpDeskTicketRequestDto);

	@Mapping(target = "userId", source = "raisedBy")
	HelpDeskTicketResponseDto toHelpDeskTicketResponseDto(HelpDeskTicket helpDeskTicket);

	List<HelpDeskTicketResponseDto> toHelpDeskTicketsResponseDto(List<HelpDeskTicket> byDocumentId);
}
