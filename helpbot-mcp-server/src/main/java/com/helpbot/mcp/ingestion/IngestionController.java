package com.helpbot.mcp.ingestion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.helpbot.mcp.s3.S3DocumentService;

import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestionController
{
	private final S3DocumentService s3DocumentService;

	@PostMapping("/all")
	public ResponseEntity<String> ingestAll()
	{
		s3DocumentService.ingestAll();
		return ResponseEntity.ok("Ingestion complete for all documents");
	}
}
