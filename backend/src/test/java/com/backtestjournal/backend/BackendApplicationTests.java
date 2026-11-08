package com.backtestjournal.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.profiles.active=test",
		"telegram.bot.token=",
		"telegram.bot.username=test_bot",
		"telegram.bot.polling-enabled=false",
		"auth.code-secret=test-code-secret-that-is-at-least-32-bytes"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
