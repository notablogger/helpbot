package com.helpbot.mcp.tools;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import com.helpbot.mcp.service.SearchService;

import lombok.RequiredArgsConstructor;


/**
 * MCP tools for searching the knowledge base.
 * <p>
 * Exposes two search variants:
 * <ul>
 *   <li>{@code search} — public search, returns only documents marked as non-internal</li>
 *   <li>{@code search_admin} — admin search, returns all documents including internal ones</li>
 * </ul>
 * Both tools perform vector similarity search against pgvector via the {@link SearchService}.
 */
@Service
@RequiredArgsConstructor
public class SearchTool
{
	private final SearchService searchService;

	/**
	 * Searches the knowledge base for public documents only.
	 * Filters out any document with {@code internal=true} metadata.
	 *
	 * @param question the user's natural language question
	 * @return list of matching document chunks formatted as strings
	 */
	@McpTool(name = "search", description = "Search the knowledge base for relevant information. Returns only public documents.")
	public List<String> search(@McpToolParam(description = "The user's question to search for in the knowledge base") String question)
	{
		return searchService.searchPublic(question);
	}

	/**
	 * Searches the knowledge base for all documents (public + internal).
	 * Intended for internal/employee use where confidential docs should be visible.
	 *
	 * @param question the user's natural language question
	 * @return list of matching document chunks formatted as strings
	 */
	@McpTool(name = "search_admin", description = "Search the knowledge base for relevant information. Returns all documents including internal/confidential ones.")
	public List<String> searchAdmin(@McpToolParam(description = "The user's question to search for in the knowledge base") String question)
	{
		return searchService.searchAll(question);
	}
}
