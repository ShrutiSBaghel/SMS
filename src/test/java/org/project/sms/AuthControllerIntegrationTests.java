package org.project.sms;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.sms.entity.User;
import org.project.sms.repository.AuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DepartmentControllerIntegrationTests.ContainerConfiguration.class)
class AuthControllerIntegrationTests {

	private final AuthRepository authRepository;

	private final PasswordEncoder passwordEncoder;

	private final ObjectMapper objectMapper;

	@LocalServerPort
	private int port;

	@Autowired
	AuthControllerIntegrationTests(AuthRepository authRepository, PasswordEncoder passwordEncoder,
			ObjectMapper objectMapper) {
		this.authRepository = authRepository;
		this.passwordEncoder = passwordEncoder;
		this.objectMapper = objectMapper;
	}

	@BeforeEach
	void clearUsers() {
		authRepository.deleteAll();
	}

	@Test
	void loginAuthenticatesUserUsingBCryptWithoutReturningCredentials() throws Exception {
		var user = new User();
		user.setName("tester");
		user.setPassword(passwordEncoder.encode("test-password"));
		authRepository.saveAndFlush(user);
		assertThat(authRepository.findByName("tester").orElseThrow().getPassword())
				.isNotEqualTo("test-password");

		var before = Instant.now();
		var response = login("{\"name\":\"tester\",\"password\":\"test-password\"}");

		assertThat(response.statusCode()).isEqualTo(200);
		var result = objectMapper.readTree(response.body());
		assertThat(result.get("authenticated").asBoolean()).isTrue();
		assertThat(result.get("name").asText()).isEqualTo("tester");
		assertThat(Instant.parse(result.get("loginTime").asText())).isBetween(before, Instant.now());
		assertThat(result.size()).isEqualTo(3);
		assertThat(response.body()).doesNotContain("test-password", "password");
	}

	@Test
	void loginReturnsSameUnauthorizedResponseForWrongPasswordAndUnknownUser() throws Exception {
		var user = new User();
		user.setName("tester");
		user.setPassword(passwordEncoder.encode("test-password"));
		authRepository.saveAndFlush(user);

		var wrongPassword = login("{\"name\":\"tester\",\"password\":\"incorrect\"}");
		var unknownUser = login("{\"name\":\"unknown\",\"password\":\"incorrect\"}");

		assertThat(wrongPassword.statusCode()).isEqualTo(401);
		assertThat(unknownUser.statusCode()).isEqualTo(401);
		assertThat(objectMapper.readTree(wrongPassword.body()))
				.isEqualTo(objectMapper.readTree("{\"authenticated\":false,\"name\":null,\"loginTime\":null}"));
		assertThat(unknownUser.body()).isEqualTo(wrongPassword.body());
	}

	@Test
	void loginRejectsMissingBlankAndMalformedRequestFields() throws Exception {
		for (var body : List.of("{}", "{\"name\":\"tester\"}",
				"{\"name\":\" \",\"password\":\"test-password\"}",
				"{\"name\":\"tester\",\"password\":\" \"}", "invalid-json")) {
			assertThat(login(body).statusCode()).isEqualTo(400);
		}
	}

	private HttpResponse<String> login(String body) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/login"))
					.timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

}

