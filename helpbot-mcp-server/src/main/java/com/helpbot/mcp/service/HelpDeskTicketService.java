package com.helpbot.mcp.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.helpbot.mcp.dto.HelpDeskTicketRequestDto;
import com.helpbot.mcp.dto.HelpDeskTicketResponseDto;
import com.helpbot.mcp.mapper.HelpDeskTicketMapper;
import com.helpbot.mcp.rds.entity.HelpDeskTicket;
import com.helpbot.mcp.rds.repository.HelpDeskTicketRepository;

import lombok.AllArgsConstructor;


@Service
@AllArgsConstructor
public class HelpDeskTicketService
{
	private final HelpDeskTicketRepository helpDeskTicketRepository;
	HelpDeskTicketMapper helpDeskTicketMapper;

	public HelpDeskTicketResponseDto createHelpDeskTicket(HelpDeskTicketRequestDto helpDeskTicket)
	{
		return helpDeskTicketMapper.toHelpDeskTicketResponseDto(
				helpDeskTicketRepository.save(helpDeskTicketMapper.toHelpDeskTicket(helpDeskTicket)));
	}

	public List<HelpDeskTicketResponseDto> findAllHelpDeskTicket(String documentId)
	{
		return helpDeskTicketMapper.toHelpDeskTicketsResponseDto(helpDeskTicketRepository.findByUserId(documentId));
	}
}
