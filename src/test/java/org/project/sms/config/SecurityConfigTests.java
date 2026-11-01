package org.project.sms.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTests {

	@Test
	void allowsConfiguredFrontendOriginsWithoutAllowingOtherVercelProjects() {
		var source = new SecurityConfig().corsConfigurationSource(List.of(
				"https://sms-ui.vercel.app", " https://sms-ui-staging.vercel.app "));
		var cors = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/auth/login"));
		assertThat(cors).isNotNull();
		assertThat(cors.checkOrigin("https://sms-ui.vercel.app")).isEqualTo("https://sms-ui.vercel.app");
		assertThat(cors.checkOrigin("https://sms-ui-staging.vercel.app")).isEqualTo("https://sms-ui-staging.vercel.app");
		assertThat(cors.checkOrigin("https://unrelated.vercel.app")).isNull();
		assertThat(cors.checkOrigin("http://localhost:4200")).isNull();
		assertThat(cors.getAllowedHeaders()).contains("Authorization", "Content-Type");
	}

}
