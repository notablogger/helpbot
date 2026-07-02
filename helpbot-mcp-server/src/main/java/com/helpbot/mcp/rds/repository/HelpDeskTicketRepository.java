package com.helpbot.mcp.rds.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.helpbot.mcp.rds.entity.HelpDeskTicket;


@Repository
public interface HelpDeskTicketRepository extends JpaRepository<HelpDeskTicket, Long>
{
	@Query("SELECT h FROM HelpDeskTicket h WHERE h.raisedBy = :userId")
	List<HelpDeskTicket> findByUserId(String userId);
}
