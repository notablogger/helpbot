package com.helpbot.mcp.s3;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.helpbot.mcp.service.S3DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
@RequiredArgsConstructor
public class S3IngestionJob
{
	private final S3DocumentService s3DocumentService;

	@Scheduled(fixedRate = 300000, initialDelay = 0)
	public void ingestDocuments()
	{
		log.info("Starting S3 document ingestion");
		s3DocumentService.ingestFromS3();
		log.info("S3 document ingestion complete");
	}
}
