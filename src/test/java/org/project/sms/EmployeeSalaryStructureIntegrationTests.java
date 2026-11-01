package org.project.sms;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.sms.entity.Employee;
import org.project.sms.entity.SalaryStructure;
import org.project.sms.repository.EmployeeRepository;
import org.project.sms.repository.SalaryStructureRepository;
import org.project.sms.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DepartmentControllerIntegrationTests.ContainerConfiguration.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class EmployeeSalaryStructureIntegrationTests {

	private final EmployeeRepository employeeRepository;

	private final SalaryStructureRepository salaryStructureRepository;

	private final ObjectMapper objectMapper;

	private final JwtService jwtService;

	@LocalServerPort
	private int port;

	@BeforeEach
	void clearEmployees() {
		salaryStructureRepository.deleteAll();
		employeeRepository.deleteAll();
	}

	@Test
	void getEmployeesEmbedsOnlyTheRequestedDepartmentsSalaryStructures() throws Exception {
		var employee = saveEmployee("Alice", 1);
		var other = saveEmployee("Bob", 2);
		var structure = new SalaryStructure();
		structure.setEmpId(employee.getId());
		structure.setFixedPay(new BigDecimal("10000.10"));
		structure.setVariablePay(new BigDecimal("2000.25"));
		structure.setBonuses(new BigDecimal("500.30"));
		structure.setDeduction(new BigDecimal("750.45"));
		salaryStructureRepository.saveAndFlush(structure);
		var otherStructure = new SalaryStructure();
		otherStructure.setEmpId(other.getId());
		otherStructure.setFixedPay(BigDecimal.ZERO);
		otherStructure.setVariablePay(BigDecimal.ZERO);
		otherStructure.setBonuses(BigDecimal.ZERO);
		otherStructure.setDeduction(BigDecimal.ZERO);
		salaryStructureRepository.saveAndFlush(otherStructure);

		var response = get("?dep_id=1");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
		var employees = objectMapper.readTree(response.body());
		assertThat(employees.isArray()).isTrue();
		assertThat(employees.size()).isEqualTo(1);
		assertThat(employees.get(0).get("id").asInt()).isEqualTo(employee.getId());
		assertThat(employees.get(0).get("salaryStructureDto")).isEqualTo(objectMapper.readTree("""
				{
				  "empId": %d,
				  "fixedPay": 10000.10,
				  "variablePay": 2000.25,
				  "bonuses": 500.30,
				  "deduction": 750.45
				}
				""".formatted(employee.getId())));
	}

	@Test
	void getEmployeesReturnsNotFoundWhenAnEmployeesSalaryStructureIsMissing() throws Exception {
		saveEmployee("Alice", 1);

		assertThat(get("?dep_id=1").statusCode()).isEqualTo(404);
	}

	@Test
	void getEmployeesRejectsMissingOrMalformedDepartmentId() throws Exception {
		for (var query : List.of("", "?dep_id=abc")) {
			assertThat(get(query).statusCode()).as(query).isEqualTo(400);
		}
	}

	private Employee saveEmployee(String name, Integer departmentId) {
		var employee = new Employee();
		employee.setName(name);
		employee.setDepartmentId(departmentId);
		employee.setSalary(new BigDecimal("11750.20"));
		employee.setCountry("India");
		return employeeRepository.saveAndFlush(employee);
	}

	private HttpResponse<String> get(String query) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/employees" + query))
					.header("Authorization", "Bearer " + jwtService.generateToken("tester"))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

}
