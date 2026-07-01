package com.doc.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HelpbotApplicationTests {

	@Test
	void contextLoads() {
	}

}
