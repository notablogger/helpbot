package com.helpbot.mcp.ingestion;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.JdkSha256HexIdGenerator;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.helpbot.mcp.config.IngestionConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService
{
	private static final int MAX_CHUNKS = 400;

	private final VectorStore vectorStore;
	private final IngestionConfig ingestionConfig;
	private final IdGenerator idGenerator = new JdkSha256HexIdGenerator();

	public boolean chunkAndIngest(Resource resource, boolean internal)
	{
		String filename = resource.getFilename();
		try
		{
			TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
			List<Document> docs = tikaDocumentReader.get();
			TextSplitter textSplitter =
					TokenTextSplitter.builder().withChunkSize(ingestionConfig.getChunkSize()).withMaxNumChunks(MAX_CHUNKS).build();
			final List<Document> split = textSplitter.split(docs);
			final List<Document> chunks = IntStream.range(0, split.size())
					.mapToObj(i -> {
						Document doc = split.get(i);
						String contentHash = idGenerator.generateId(doc.getText());
						doc.getMetadata().put("internal", internal);
						doc.getMetadata().put("source", filename);
						doc.getMetadata().put("contentHash", contentHash);
						// id keys on (source, index, content) - unchanged content at the same
						// position lands on the same id, so re-ingestion upserts it in place;
						// changed content gets a new id and orphans the old one (cleaned up below)
						String id = idGenerator.generateId(filename, String.valueOf(i), doc.getText());
						return new Document(id, doc.getText(), doc.getMetadata());
					})
					.toList();

			Set<String> currentIds = chunks.stream().map(Document::getId).collect(Collectors.toSet());
			Set<String> existingIds = findExistingChunkIds(filename);

			List<Document> toEmbed = chunks.stream().filter(c -> !existingIds.contains(c.getId())).toList();
			List<String> orphanIds = existingIds.stream().filter(id -> !currentIds.contains(id)).toList();

			if (!toEmbed.isEmpty())
			{
				vectorStore.add(toEmbed);
			}
			if (!orphanIds.isEmpty())
			{
				vectorStore.delete(orphanIds);
			}

			log.info(
					"Ingested {}: {} chunks total, {} unchanged (skipped embedding), {} embedded, {} orphaned chunks removed",
					filename, chunks.size(), chunks.size() - toEmbed.size(), toEmbed.size(), orphanIds.size());
			return true;
		}
		catch (Exception e)
		{
			log.error("Error ingesting {}: {}", filename, e.getMessage(), e);
			return false;
		}
	}

	private Set<String> findExistingChunkIds(String filename)
	{
		Filter.Expression filter = new FilterExpressionBuilder().eq("source", filename).build();
		SearchRequest request =
				SearchRequest.builder().query(filename).topK(MAX_CHUNKS).similarityThresholdAll().filterExpression(filter).build();
		return vectorStore.similaritySearch(request).stream().map(Document::getId).collect(Collectors.toSet());
	}
}