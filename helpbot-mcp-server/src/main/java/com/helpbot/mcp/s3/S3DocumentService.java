package com.helpbot.mcp.s3;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.helpbot.mcp.ingestion.IngestionService;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;


@Service
@Slf4j
@RequiredArgsConstructor
public class S3DocumentService
{
	private static final String INTERNAL_PREFIX = "internal/";
	private static final String PUBLIC_PREFIX = "public/";

	private final S3Template s3Template;
	private final S3Client s3Client;
	private final IngestionService ingestionService;

	@Value("${helpbot.s3.bucket}")
	private String bucket;

	public Resource download(String key)
	{
		return s3Template.download(bucket, key);
	}

	public void ingestAll()
	{
		ingestFolder(INTERNAL_PREFIX, true);
		ingestFolder(PUBLIC_PREFIX, false);
	}

	public void ingestFolder(String prefix, boolean internal)
	{
		List<S3Object> objects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
				.bucket(bucket)
				.prefix(prefix)
				.build()).contents();

		for (S3Object obj : objects)
		{
			String key = obj.key();
			if (key.endsWith("/"))
			{
				continue; // skip folder markers
			}
			log.info("Downloading s3://{}/{} (internal={})", bucket, key, internal);
			Resource resource = download(key);
			ingestionService.chunkAndIngest(resource, internal);
		}
	}
}
