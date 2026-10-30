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
import org.project.sms.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DepartmentControllerIntegrationTests.ContainerConfiguration.class)
class EmployeeControllerIntegrationTests {

	private final EmployeeRepository employeeRepository;

	private final ObjectMapper objectMapper;

	@LocalServerPort
	private int port;

	@Autowired
	EmployeeControllerIntegrationTests(EmployeeRepository employeeRepository, ObjectMapper objectMapper) {
		this.employeeRepository = employeeRepository;
		this.objectMapper = objectMapper;
	}

	@BeforeEach
	void clearEmployees() {
		employeeRepository.deleteAll();
	}

	@Test
	void getEmpFiltersDepartmentAndReturnsSeparatePagesInIdOrder() throws Exception {
		var first = employee("Alice", 1);
		var second = employee("Bob", 1);
		var third = employee("Charlie", 1);
		employeeRepository.saveAllAndFlush(List.of(first, employee("Other department", 2), second, third));

		var firstPage = get("/employees?dep_id=1&page=0&size=2");
		var secondPage = get("/employees?dep_id=1&page=1&size=2");

		assertThat(firstPage.statusCode()).isEqualTo(200);
		assertThat(secondPage.statusCode()).isEqualTo(200);
		var firstResults = objectMapper.readTree(firstPage.body());
		var secondResults = objectMapper.readTree(secondPage.body());
		assertThat(firstResults.isArray()).isTrue();
		assertThat(firstResults.size()).isEqualTo(2);
		assertThat(firstResults.get(0).get("id").asInt()).isEqualTo(first.getId());
		assertThat(firstResults.get(1).get("id").asInt()).isEqualTo(second.getId());
		assertThat(firstResults.get(0).get("name").asText()).isEqualTo("Alice");
		assertThat(firstResults.get(0).get("departmentId").asInt()).isEqualTo(1);
		assertThat(firstResults.get(0).get("salary").decimalValue()).isEqualByComparingTo("12345.67");
		assertThat(firstResults.get(0).get("country").asText()).isEqualTo("India");
		assertThat(secondResults.size()).isEqualTo(1);
		assertThat(secondResults.get(0).get("id").asInt()).isEqualTo(third.getId());
	}

	@Test
	void getEmpUsesDefaultPageSize() throws Exception {
		var employees = java.util.stream.IntStream.range(0, 21)
				.mapToObj(index -> employee("Employee " + index, 1))
				.toList();
		employeeRepository.saveAllAndFlush(employees);

		var response = get("/employees?dep_id=1");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(objectMapper.readTree(response.body()).size()).isEqualTo(20);
	}

	@Test
	void getEmpReturnsEmptyArrayForMissingDepartmentAndPastLastPage() throws Exception {
		employeeRepository.saveAndFlush(employee("Alice", 1));

		for (var path : List.of("/employees?dep_id=999", "/employees?dep_id=1&page=1&size=2")) {
			var response = get(path);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(objectMapper.readTree(response.body())).isEqualTo(objectMapper.readTree("[]"));
		}
	}

	@Test
	void getEmpRejectsInvalidPaginationAndMissingDepartment() throws Exception {
		for (var query : List.of("dep_id=1&page=-1", "dep_id=1&size=0", "dep_id=1&size=101",
				"dep_id=1&page=abc", "page=0")) {
			assertThat(get("/employees?" + query).statusCode()).as(query).isEqualTo(400);
		}
	}

	@Test
	void filterEmployeeReturnsNotImplemented() throws Exception {
		assertThat(get("/employees/filter").statusCode()).isEqualTo(501);
	}

	private Employee employee(String name, Integer departmentId) {
		var employee = new Employee();
		employee.setName(name);
		employee.setDepartmentId(departmentId);
		employee.setSalary(new BigDecimal("12345.67"));
		employee.setCountry("India");
		return employee;
	}

	private HttpResponse<String> get(String path) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

}
