package com.helpbot.mcp;

import java.nio.file.Path;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	LocalStackContainer localStackContainer() {
		return new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.12.0"))
			.withEnv("HELPBOT_S3_BUCKET", "helpbot-documents")
			.withCopyFileToContainer(
				MountableFile.forHostPath(Path.of("localstack/init-s3-bucket.sh"), 0744),
				"/etc/localstack/init/ready.d/init-s3-bucket.sh");
	}

	@Bean
	@ServiceConnection
	OllamaContainer ollamaContainer() {
		return new OllamaContainer(DockerImageName.parse("ollama/ollama:latest"));
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer pgvectorContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"));
	}

}
