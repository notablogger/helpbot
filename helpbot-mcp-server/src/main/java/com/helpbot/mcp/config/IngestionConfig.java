package com.helpbot.mcp.config;


import org.springframework.context.annotation.Configuration;

import lombok.Data;


@Configuration("helpbot.ingestion")
@Data
public class IngestionConfig
{
	int chunkSize;
	int overlap;
}
