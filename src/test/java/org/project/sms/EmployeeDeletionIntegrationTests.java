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
import org.project.sms.entity.SalaryStructure;
import org.project.sms.repository.DepartmentRepository;
import org.project.sms.repository.EmployeeRepository;
import org.project.sms.repository.SalaryStructureRepository;
import org.project.sms.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DepartmentControllerIntegrationTests.ContainerConfiguration.class)
class EmployeeDeletionIntegrationTests {

	private final EmployeeRepository employees;
	private final SalaryStructureRepository structures;
	private final DepartmentRepository departments;
	private final JwtService jwt;
	private final ObjectMapper mapper;
	private final JdbcTemplate jdbc;

	private Integer employeeId;
	private Integer otherEmployeeId;

	@LocalServerPort
	private int port;

	@Autowired
	EmployeeDeletionIntegrationTests(EmployeeRepository employees, SalaryStructureRepository structures,
			DepartmentRepository departments, JwtService jwt, ObjectMapper mapper, JdbcTemplate jdbc) {
		this.employees = employees;
		this.structures = structures;
		this.departments = departments;
		this.jwt = jwt;
		this.mapper = mapper;
		this.jdbc = jdbc;
	}

	@BeforeEach
	void prepareEmployees() {
		structures.deleteAll();
		employees.deleteAll();
		departments.deleteAll();
		var department = new Department();
		department.setName("Engineering");
		int departmentId = departments.saveAndFlush(department).getId();
		employeeId = seedEmployee("Alice", departmentId);
		otherEmployeeId = seedEmployee("Bob", departmentId);
	}

	private Integer seedEmployee(String name, int departmentId) {
		var employee = new Employee();
		employee.setName(name);
		employee.setDepartmentId(departmentId);
		employee.setCountry("India");
		employee.setSalary(new BigDecimal("1000.00"));
		int id = employees.saveAndFlush(employee).getId();
		var structure = new SalaryStructure();
		structure.setEmpId(id);
		structure.setFixedPay(new BigDecimal("1000.00"));
		structure.setVariablePay(BigDecimal.ZERO);
		structure.setBonuses(BigDecimal.ZERO);
		structure.setDeduction(BigDecimal.ZERO);
		structures.saveAndFlush(structure);
		return id;
	}

	@Test
	void deletesBothRowsWithForeignKeyAndLeavesOtherEmployeesAndDepartmentIntact() throws Exception {
		jdbc.execute("ALTER TABLE salary_structure ADD CONSTRAINT test_delete_salary_fk FOREIGN KEY (emp_id) REFERENCES employee(id)");
		try {
			var response = delete(employeeId.toString(), true);
			assertThat(response.statusCode()).isEqualTo(200);
			var result = mapper.readTree(response.body());
			assertThat(result.get("id").asInt()).isEqualTo(employeeId);
			assertThat(result.get("message").asText()).isEqualTo("Employee Deleted Successfully");
			assertThat(employees.existsById(employeeId)).isFalse();
			assertThat(structures.existsById(employeeId)).isFalse();
			assertThat(employees.existsById(otherEmployeeId)).isTrue();
			assertThat(structures.existsById(otherEmployeeId)).isTrue();
			assertThat(departments.count()).isEqualTo(1);
			assertThat(delete(employeeId.toString(), true).statusCode()).isEqualTo(404);
		} finally {
			jdbc.execute("ALTER TABLE salary_structure DROP CONSTRAINT test_delete_salary_fk");
		}
	}

	@Test
	void deletesAnEmployeeWhoseSalaryStructureIsAlreadyMissing() throws Exception {
		structures.deleteById(employeeId);
		assertThat(delete(employeeId.toString(), true).statusCode()).isEqualTo(200);
		assertThat(employees.existsById(employeeId)).isFalse();
		assertThat(structures.existsById(employeeId)).isFalse();
	}

	@Test
	void rejectsUnknownIdsInvalidIdsAndUnauthenticatedRequestsWithoutChangingData() throws Exception {
		assertThat(delete(employeeId.toString(), false).statusCode()).isEqualTo(401);
		assertThat(delete(Integer.toString(Integer.MAX_VALUE), true).statusCode()).isEqualTo(404);
		for (String id : List.of("0", "-1", "abc")) {
			assertThat(delete(id, true).statusCode()).isEqualTo(400);
		}
		assertThat(employees.count()).isEqualTo(2);
		assertThat(structures.count()).isEqualTo(2);
	}

	@Test
	void employeeDeletionFailureRollsBackPreviouslyFlushedSalaryDeletion() throws Exception {
		jdbc.execute("CREATE TABLE test_delete_blocker (employee_id integer REFERENCES employee(id))");
		try {
			jdbc.update("INSERT INTO test_delete_blocker (employee_id) VALUES (?)", employeeId);
			assertThat(delete(employeeId.toString(), true).statusCode()).isEqualTo(500);
			assertThat(employees.existsById(employeeId)).isTrue();
			assertThat(structures.existsById(employeeId)).isTrue();
			assertThat(structures.findById(employeeId).orElseThrow().getFixedPay()).isEqualByComparingTo("1000.00");
			assertThat(employees.count()).isEqualTo(2);
			assertThat(structures.count()).isEqualTo(2);
		} finally {
			jdbc.execute("DROP TABLE test_delete_blocker");
		}
	}

	private HttpResponse<String> delete(String id, boolean authenticated) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/employees/" + id))
					.timeout(Duration.ofSeconds(10));
			if (authenticated) request.header("Authorization", "Bearer " + jwt.generateToken("tester"));
			return client.send(request.DELETE().build(), HttpResponse.BodyHandlers.ofString());
		}
	}

}
