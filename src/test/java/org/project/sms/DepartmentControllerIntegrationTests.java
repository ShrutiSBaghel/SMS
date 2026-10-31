package org.project.sms;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.sms.entity.Department;
import org.project.sms.repository.DepartmentRepository;
import org.project.sms.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DepartmentControllerIntegrationTests.ContainerConfiguration.class)
class DepartmentControllerIntegrationTests {

	@TestConfiguration(proxyBeanMethods = false)
	static class ContainerConfiguration {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer("postgres:17-alpine");
		}

	}

	private final DepartmentRepository departmentRepository;

	private final ObjectMapper objectMapper;

	private final JwtService jwtService;

	@LocalServerPort
	private int port;

	@Autowired
	DepartmentControllerIntegrationTests(DepartmentRepository departmentRepository, ObjectMapper objectMapper,
			JwtService jwtService) {
		this.departmentRepository = departmentRepository;
		this.objectMapper = objectMapper;
		this.jwtService = jwtService;
	}

	@BeforeEach
	void clearDepartments() {
		departmentRepository.deleteAll();
	}

	@Test
	void getDepartmentsReturnsAllDepartmentsWithEmployeeCountPlaceholder() throws Exception {
		var engineering = new Department();
		engineering.setName("Engineering");
		var finance = new Department();
		finance.setName("Finance");
		departmentRepository.saveAllAndFlush(List.of(engineering, finance));

		var response = getDepartments();

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type").orElseThrow())
				.startsWith("application/json");
		var actual = objectMapper.readTree(response.body());
		var expected = objectMapper.readTree("""
				[
				  {"id": %d, "name": "Engineering", "totalEmployees": 0},
				  {"id": %d, "name": "Finance", "totalEmployees": 0}
				]
				""".formatted(engineering.getId(), finance.getId()));
		assertThat(actual.isArray()).isTrue();
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void getDepartmentsReturnsEmptyArrayWhenNoDepartmentsExist() throws Exception {
		var response = getDepartments();

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(objectMapper.readTree(response.body())).isEqualTo(objectMapper.readTree("[]"));
	}

	private HttpResponse<String> getDepartments() throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/departments"))
					.header("Authorization", "Bearer " + jwtService.generateToken("tester"))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

}
