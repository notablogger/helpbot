package com.doc.rag;

import org.springframework.boot.SpringApplication;

public class TestHelpbotApplication {

	public static void main(String[] args) {
		SpringApplication.from(HelpbotApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
