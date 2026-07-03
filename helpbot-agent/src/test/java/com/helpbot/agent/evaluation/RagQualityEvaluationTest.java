package com.helpbot.agent.evaluation;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Judge-LLM quality checks for chat answers, per the plan in ARCHITECTURE.md's
 * "Testing" section: given a question, the chunks {@code search}/{@code search_admin}
 * would have retrieved, and the answer the agent produced, is the answer relevant to
 * the question and grounded in the retrieved chunks (not hallucinated)?
 * <p>
 * Deliberately not a {@code @SpringBootTest} against {@code HelpbotAgentApplication} -
 * that context fails to start without a reachable {@code helpbot-mcp-server} (see
 * CLAUDE.md's MCP-client gotcha), which this test has no need for. Instead it spins up
 * a minimal, isolated context via {@link ApplicationContextRunner} with just the OpenAI
 * chat + {@link ChatClient.Builder} auto-configuration, to act as the judge model - the
 * real {@code helpbot-mcp-server}/MCP wiring is never loaded.
 * <p>
 * Requires a real {@code OPENAI_API_KEY} - skipped, not failed, when one isn't set, so
 * it doesn't run against CI's placeholder key.
 * <p>
 * The question/context/answer fixtures below are illustrative, not derived from real
 * seeded documents (this repo ships no committed seed content under
 * {@code helpbot-mcp-server/localstack/documents/} - see that module's README). Swap
 * in real golden questions once real documents exist.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RagQualityEvaluationTest {

	private static final ApplicationContextRunner JUDGE_CONTEXT = new ApplicationContextRunner()
		.withBean(ToolCallingManager.class, () -> DefaultToolCallingManager.builder().build())
		.withConfiguration(
				AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ChatClientAutoConfiguration.class))
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
				"spring.ai.openai.chat.model=gpt-4o-mini");

	private record Fixture(String name, String question, List<String> context, String answer, boolean expectRelevant,
			boolean expectGrounded) {
	}

	private static Stream<Fixture> fixtures() {
		return Stream.of(
				new Fixture("grounded answer to the question asked", "What is your return policy?",
						List.of("Items may be returned within 30 days of purchase for a full refund, "
								+ "provided they are unused and in original packaging."),
						"You can return items within 30 days of purchase for a full refund, as long as "
								+ "they're unused and in their original packaging.",
						true, true),
				new Fixture("hallucinated detail not present in context", "What is your return policy?",
						List.of("Items may be returned within 30 days of purchase for a full refund, "
								+ "provided they are unused and in original packaging."),
						"You can return items within 90 days of purchase for a full refund, and we also "
								+ "cover return shipping costs.",
						true, false),
				new Fixture("answer to a different question than the one asked", "What is your return policy?",
						List.of("Items may be returned within 30 days of purchase for a full refund, "
								+ "provided they are unused and in original packaging."),
						"Our support team is available Monday through Friday, 9am to 6pm.", false, true));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("fixtures")
	void relevancyMatchesExpectation(Fixture fixture) {
		JUDGE_CONTEXT.run(context -> {
			RelevancyEvaluator evaluator = new RelevancyEvaluator(context.getBean(ChatClient.Builder.class));

			EvaluationRequest request = new EvaluationRequest(fixture.question(), toDocuments(fixture.context()),
					fixture.answer());
			EvaluationResponse response = evaluator.evaluate(request);

			assertThat(response.isPass()).as("relevancy of: %s", fixture.name()).isEqualTo(fixture.expectRelevant());
		});
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("fixtures")
	void groundednessMatchesExpectation(Fixture fixture) {
		JUDGE_CONTEXT.run(context -> {
			FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder(context.getBean(ChatClient.Builder.class))
				.build();

			EvaluationRequest request = new EvaluationRequest(fixture.question(), toDocuments(fixture.context()),
					fixture.answer());
			EvaluationResponse response = evaluator.evaluate(request);

			assertThat(response.isPass()).as("groundedness of: %s", fixture.name())
				.isEqualTo(fixture.expectGrounded());
		});
	}

	private static List<Document> toDocuments(List<String> chunks) {
		return chunks.stream().map(Document::new).toList();
	}

}
