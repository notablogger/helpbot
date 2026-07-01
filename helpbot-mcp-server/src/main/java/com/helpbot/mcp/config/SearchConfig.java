package com.helpbot.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;


@ConfigurationProperties("helpbot.search")
@Data
public class SearchConfig
{
	int topK;
	float minSimilarity;
}
