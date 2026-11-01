package org.project.sms;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.project.sms.entity.Department;
import org.project.sms.entity.Employee;
import org.project.sms.entity.User;
import org.project.sms.repository.AuthRepository;
import org.project.sms.repository.DepartmentRepository;
import org.project.sms.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ProductionDeploymentIntegrationTests {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
	private final AuthRepository users;
	private final DepartmentRepository departments;
	private final EmployeeRepository employees;
	private final PasswordEncoder passwords;
	private final ObjectMapper mapper;
	@LocalServerPort private int port;

	@Autowired
	ProductionDeploymentIntegrationTests(AuthRepository users, DepartmentRepository departments,
			EmployeeRepository employees, PasswordEncoder passwords, ObjectMapper mapper) {
		this.users = users;
		this.departments = departments;
		this.employees = employees;
		this.passwords = passwords;
		this.mapper = mapper;
	}

	@DynamicPropertySource
	static void productionDatabase(DynamicPropertyRegistry properties) throws Exception {
		POSTGRES.start();
		try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword()); var sql = connection.createStatement()) {
			// Supabase provides these roles; emulate them only inside this disposable database.
			sql.execute("create role anon");
			sql.execute("create role authenticated");
			sql.execute(Files.readString(Path.of("db/migrations/V001__initial_schema.sql")));
		}
		properties.add("SUPABASE_DB_URL", POSTGRES::getJdbcUrl);
		properties.add("SUPABASE_DB_USERNAME", POSTGRES::getUsername);
		properties.add("SUPABASE_DB_PASSWORD", POSTGRES::getPassword);
		properties.add("CORS_ALLOWED_ORIGINS", () -> "https://sms-ui-test.vercel.app");
		// Public test-only key; the prod profile requires this environment placeholder explicitly.
		properties.add("JWT_SECRET", () -> Base64.getEncoder().encodeToString(new byte[32]));
	}

	@AfterAll
	static void stopDatabase() {
		POSTGRES.stop();
	}

	@Test
	void productionSchemaSupportsLoginCorsAndBothSalaryReports() throws Exception {
		var user = new User();
		user.setName("deployment-tester");
		user.setPassword(passwords.encode("test-only-password"));
		users.saveAndFlush(user);
		var department = new Department();
		department.setName("Engineering");
		departments.saveAndFlush(department);
		var employee = new Employee();
		employee.setDepartmentId(department.getId());
		employee.setCountry("India");
		employee.setSalary(new BigDecimal("1234.56"));
		employees.saveAndFlush(employee);

		try (var client = HttpClient.newHttpClient()) {
			var health = client.send(request("/health").GET().build(), HttpResponse.BodyHandlers.ofString());
			assertThat(health.statusCode()).isEqualTo(200);
			assertThat(mapper.readTree(health.body()).get("status").asText()).isEqualTo("UP");
			assertThat(client.send(request("/departments").GET().build(),
					HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
			var preflight = client.send(request("/auth/login")
					.header("Origin", "https://sms-ui-test.vercel.app")
					.header("Access-Control-Request-Method", "POST")
					.header("Access-Control-Request-Headers", "content-type")
					.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(preflight.statusCode()).isEqualTo(200);
			assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin"))
					.contains("https://sms-ui-test.vercel.app");
			var login = client.send(request("/auth/login").header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(
							"{\"name\":\"deployment-tester\",\"password\":\"test-only-password\"}")).build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(login.statusCode()).isEqualTo(200);
			var token = mapper.readTree(login.body()).get("token").asText();
			for (var groupBy : new String[] {"department", "country"}) {
				var response = client.send(request("/reports/salary-distribution?groupBy=" + groupBy)
						.header("Authorization", "Bearer " + token).GET().build(),
						HttpResponse.BodyHandlers.ofString());
				assertThat(response.statusCode()).isEqualTo(200);
				var data = mapper.readTree(response.body());
				assertThat(data.get("averageSalary").decimalValue()).isEqualByComparingTo("1234.56");
				assertThat(data.get("groups").get(0).get("label").asText())
						.isEqualTo(groupBy.equals("country") ? "India" : "Engineering");
			}
		}
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(20));
	}

}
