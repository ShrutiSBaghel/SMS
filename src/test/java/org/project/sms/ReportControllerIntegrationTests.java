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
import org.project.sms.entity.Department;
import org.project.sms.entity.Employee;
import org.project.sms.repository.DepartmentRepository;
import org.project.sms.repository.EmployeeRepository;
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
class ReportControllerIntegrationTests {

	private final EmployeeRepository employees;
	private final DepartmentRepository departments;
	private final JwtService jwt;
	private final ObjectMapper mapper;
	@LocalServerPort private int port;

	@Autowired
	ReportControllerIntegrationTests(EmployeeRepository employees, DepartmentRepository departments,
			JwtService jwt, ObjectMapper mapper) {
		this.employees = employees;
		this.departments = departments;
		this.jwt = jwt;
		this.mapper = mapper;
	}

	@BeforeEach
	void clearData() {
		employees.deleteAll();
		departments.deleteAll();
	}

	@Test
	void aggregatesDepartmentsByIdIncludingUnassignedAndMissingSalaries() throws Exception {
		var first = new Department();
		first.setName("Engineering");
		var second = new Department();
		second.setName("Engineering");
		departments.saveAllAndFlush(List.of(first, second));
		employee(first.getId(), "India", "100.10");
		employee(first.getId(), "India", "50.20");
		employee(first.getId(), "India", null);
		employee(second.getId(), "Canada", "49.70");
		employee(null, null, "0");
		var response = get("department", true);
		assertThat(response.statusCode()).isEqualTo(200);
		var data = mapper.readTree(response.body());
		assertThat(data.get("totalSalary").decimalValue()).isEqualByComparingTo("200");
		assertThat(data.get("employeeCount").asInt()).isEqualTo(5);
		assertThat(data.get("missingSalaryCount").asInt()).isEqualTo(1);
		assertThat(data.get("groups").size()).isEqualTo(3);
		assertThat(data.get("groups").get(0).get("totalSalary").decimalValue()).isEqualByComparingTo("150.30");
		assertThat(data.get("groups").get(0).get("percentage").decimalValue()).isEqualByComparingTo("75.15");
		assertThat(data.get("groups").get(2).get("label").asText()).isEqualTo("Unassigned");
	}

	@Test
	void combinesCountryCaseAndWhitespaceAndKeepsZeroTotalsSafe() throws Exception {
		employee(null, " India ", "10");
		employee(null, "india", "20");
		employee(null, " ", null);
		var data = mapper.readTree(get("country", true).body());
		assertThat(data.get("groups").size()).isEqualTo(2);
		assertThat(data.get("groups").get(0).get("employeeCount").asInt()).isEqualTo(2);
		assertThat(data.get("groups").get(0).get("totalSalary").decimalValue()).isEqualByComparingTo("30");
		employees.deleteAll();
		employee(null, null, null);
		data = mapper.readTree(get("country", true).body());
		assertThat(data.get("totalSalary").decimalValue()).isZero();
		assertThat(data.get("groups").get(0).get("percentage").decimalValue()).isZero();
	}

	@Test
	void handlesEmptyDataRejectsUnsupportedGroupingAndRequiresAuthentication() throws Exception {
		assertThat(mapper.readTree(get("department", true).body()).get("groups").size()).isZero();
		assertThat(get("role", true).statusCode()).isEqualTo(400);
		assertThat(get("country", false).statusCode()).isEqualTo(401);
	}

	private void employee(Integer department, String country, String salary) {
		var employee = new Employee();
		employee.setDepartmentId(department);
		employee.setCountry(country);
		employee.setSalary(salary == null ? null : new BigDecimal(salary));
		employees.saveAndFlush(employee);
	}

	private HttpResponse<String> get(String groupBy, boolean authenticated) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
					+ "/reports/salary-distribution?groupBy=" + groupBy)).timeout(Duration.ofSeconds(10));
			if (authenticated) request.header("Authorization", "Bearer " + jwt.generateToken("tester"));
			return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
		}
	}

}
