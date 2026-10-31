package org.project.sms;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.sms.entity.SalaryStructure;
import org.project.sms.repository.SalaryStructureRepository;
import org.project.sms.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DepartmentControllerIntegrationTests.ContainerConfiguration.class)
class SalaryStructureControllerIntegrationTests {

	private final SalaryStructureRepository salaryStructureRepository;

	private final ObjectMapper objectMapper;

	private final JwtService jwtService;

	@LocalServerPort
	private int port;

	@Autowired
	SalaryStructureControllerIntegrationTests(SalaryStructureRepository salaryStructureRepository,
			ObjectMapper objectMapper, JwtService jwtService) {
		this.salaryStructureRepository = salaryStructureRepository;
		this.objectMapper = objectMapper;
		this.jwtService = jwtService;
	}

	@BeforeEach
	void clearSalaryStructures() {
		salaryStructureRepository.deleteAll();
	}

	@Test
	void getSalaryStructureReturnsOnlyRequestedEmployeeWithVariablePay() throws Exception {
		var structure = new SalaryStructure();
		structure.setEmpId(1);
		structure.setFixedPay(new BigDecimal("10000.10"));
		structure.setVariablePay(new BigDecimal("2000.25"));
		structure.setBonuses(new BigDecimal("500.30"));
		structure.setDeduction(new BigDecimal("750.45"));
		salaryStructureRepository.saveAndFlush(structure);
		var other = new SalaryStructure();
		other.setEmpId(2);
		other.setFixedPay(BigDecimal.ZERO);
		other.setVariablePay(BigDecimal.ZERO);
		other.setBonuses(BigDecimal.ZERO);
		other.setDeduction(BigDecimal.ZERO);
		salaryStructureRepository.saveAndFlush(other);

		var response = get("?emp_id=1");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
		assertThat(objectMapper.readTree(response.body())).isEqualTo(objectMapper.readTree("""
				{
				  "empId": 1,
				  "fixedPay": 10000.10,
				  "variablePay": 2000.25,
				  "bonuses": 500.30,
				  "deduction": 750.45
				}
				"""));
	}

	@Test
	void getSalaryStructureReturnsNotFoundWhenMissing() throws Exception {
		assertThat(get("?emp_id=99").statusCode()).isEqualTo(404);
	}

	@Test
	void getSalaryStructureRejectsMissingOrMalformedEmployeeId() throws Exception {
		assertThat(get("").statusCode()).isEqualTo(400);
		assertThat(get("?emp_id=abc").statusCode()).isEqualTo(400);
	}

	private HttpResponse<String> get(String query) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/salary-structures" + query))
					.header("Authorization", "Bearer " + jwtService.generateToken("tester"))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

}
