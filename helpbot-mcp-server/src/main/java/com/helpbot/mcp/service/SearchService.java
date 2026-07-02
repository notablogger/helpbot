package com.helpbot.mcp.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import com.helpbot.mcp.config.SearchConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
@RequiredArgsConstructor
public class SearchService
{
	private final VectorStore vectorStore;
	private final SearchConfig searchConfig;

	public List<String> searchPublic(String question)
	{
		log.info("Public search request: '{}'", question);
		Filter.Expression filter = new FilterExpressionBuilder().eq("internal", false).build();
		return executeSearch(question, filter);
	}

	public List<String> searchAll(String question)
	{
		log.info("Admin search request: '{}'", question);
		Filter.Expression filter = new FilterExpressionBuilder().in("internal", true, false).build();
		return executeSearch(question, filter);
	}

	private List<String> executeSearch(String question, Filter.Expression filter)
	{
		SearchRequest request = SearchRequest.builder()
				.query(question)
				.topK(searchConfig.getTopK())
				.similarityThreshold(searchConfig.getMinSimilarity())
				.filterExpression(filter)
				.build();

		List<Document> results = vectorStore.similaritySearch(request);
		log.info("Search returned {} results for query: '{}'", results.size(), question);

		return results.stream()
				.map(Document::getFormattedContent)
				.toList();
	}
}

