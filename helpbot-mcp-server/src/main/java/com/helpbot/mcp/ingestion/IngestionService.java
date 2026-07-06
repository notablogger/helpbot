package com.helpbot.mcp.ingestion;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.document.id.JdkSha256HexIdGenerator;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
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
	private final VectorStore vectorStore;
	private final IngestionConfig ingestionConfig;
	private final IdGenerator idGenerator = new JdkSha256HexIdGenerator();

	public boolean chunkAndIngest(Resource resource, boolean internal)
	{
		try
		{
			TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
			List<Document> docs = tikaDocumentReader.get();
			TextSplitter textSplitter =
					TokenTextSplitter.builder().withChunkSize(ingestionConfig.getChunkSize()).withMaxNumChunks(400).build();
			final List<Document> split = textSplitter.split(docs);
			final List<Document> idStamped = split.stream()
					.map(doc -> {
						doc.getMetadata().put("internal", internal);
						doc.getMetadata().put("source", resource.getFilename());
						// deterministic id (source + content) lets PgVectorStore's upsert-by-id
						// replace an unchanged chunk instead of duplicating it on re-ingestion
						String id = idGenerator.generateId(resource.getFilename(), doc.getText());
						return new Document(id, doc.getText(), doc.getMetadata());
					})
					.toList();
			vectorStore.add(idStamped);
			log.info("Successfully chunked and ingested {}, total chunks: {}", resource.getFilename(), idStamped.size());
			return true;
		}
		catch (Exception e)
		{
			log.error("Error ingesting {}: {}", resource.getFilename(), e.getMessage(), e);
			return false;
		}
	}
}