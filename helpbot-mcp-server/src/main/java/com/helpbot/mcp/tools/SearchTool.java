package com.helpbot.mcp.tools;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import com.helpbot.mcp.config.SearchConfig;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class SearchTool
{
	private final VectorStore vectorStore;
	private final SearchConfig searchConfig;

	@McpTool(name = "search", description = "Search the knowledge base for relevant information.")
	public List<String> search(@McpToolParam(description = "User Question?") String question)
	{
		org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression = new FilterExpressionBuilder().eq("internal",
				false).build();
		return vectorStore.similaritySearch(
						SearchRequest.builder().query(question).topK(searchConfig.getTopK()).similarityThreshold(
										searchConfig.getMinSimilarity()).
								filterExpression(filterExpression).build())
				.stream()
				.map(Document::getFormattedContent)
				.toList();
	}

	@McpTool(name = "search_admin", description = "Search the knowledge base for relevant information.")
	public List<String> searchAdmin(@McpToolParam(description = "User Question?") String question)
	{
		org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression = new FilterExpressionBuilder().in("internal",
				true, false).build();
		return vectorStore.similaritySearch(SearchRequest.builder().topK(searchConfig.getTopK()).similarityThreshold(
						searchConfig.getMinSimilarity()).query(question).filterExpression(filterExpression).build())
				.stream()
				.map(Document::getFormattedContent)
				.toList();
	}
}
