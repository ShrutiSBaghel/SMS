package org.project.sms;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

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
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DepartmentControllerIntegrationTests.ContainerConfiguration.class)
class EmployeeUpdateIntegrationTests {

	private final EmployeeRepository employeeRepository;

	private final SalaryStructureRepository salaryStructureRepository;

	private final DepartmentRepository departmentRepository;

	private final JwtService jwtService;

	private final ObjectMapper objectMapper;

	private final JdbcTemplate jdbcTemplate;

	private Integer departmentId;

	private Integer employeeId;

	@LocalServerPort
	private int port;

	@Autowired
	EmployeeUpdateIntegrationTests(EmployeeRepository employeeRepository,
			SalaryStructureRepository salaryStructureRepository, DepartmentRepository departmentRepository,
			JwtService jwtService, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
		this.employeeRepository = employeeRepository;
		this.salaryStructureRepository = salaryStructureRepository;
		this.departmentRepository = departmentRepository;
		this.jwtService = jwtService;
		this.objectMapper = objectMapper;
		this.jdbcTemplate = jdbcTemplate;
	}

	@BeforeEach
	void prepareExistingEmployee() {
		salaryStructureRepository.deleteAll();
		employeeRepository.deleteAll();
		departmentRepository.deleteAll();
		var department = new Department();
		department.setName("Engineering");
		departmentId = departmentRepository.saveAndFlush(department).getId();
		var employee = new Employee();
		employee.setName("Alice");
		employee.setDepartmentId(departmentId);
		employee.setCountry("India");
		employee.setSalary(new BigDecimal("56000.00"));
		employeeId = employeeRepository.saveAndFlush(employee).getId();
		var structure = new SalaryStructure();
		structure.setEmpId(employeeId);
		structure.setFixedPay(new BigDecimal("50000.00"));
		structure.setVariablePay(new BigDecimal("5000.00"));
		structure.setBonuses(new BigDecimal("2000.00"));
		structure.setDeduction(new BigDecimal("1000.00"));
		salaryStructureRepository.saveAndFlush(structure);
	}

	@Test
	void updateEmployeeChangesBothRowsAndDepartmentWhilePreservingIds() throws Exception {
		var department = new Department();
		department.setName("Finance");
		int newDepartmentId = departmentRepository.saveAndFlush(department).getId();

		var response = put(employeeId.toString(), request().put("departmentId", newDepartmentId), true);

		assertThat(response.statusCode()).isEqualTo(200);
		var result = objectMapper.readTree(response.body());
		assertThat(result.get("id").asInt()).isEqualTo(employeeId);
		assertThat(result.get("salaryStructureDto").get("empId").asInt()).isEqualTo(employeeId);
		assertThat(result.get("name").asText()).isEqualTo("Bob");
		assertThat(result.get("country").asText()).isEqualTo("Canada");
		assertThat(result.get("salary").decimalValue()).isEqualByComparingTo("67000");
		var employee = employeeRepository.findById(employeeId).orElseThrow();
		assertThat(employee.getName()).isEqualTo("Bob");
		assertThat(employee.getCountry()).isEqualTo("Canada");
		assertThat(employee.getDepartmentId()).isEqualTo(newDepartmentId);
		assertThat(employee.getSalary()).isEqualByComparingTo("67000");
		var structure = salaryStructureRepository.findById(employeeId).orElseThrow();
		assertThat(structure.getFixedPay()).isEqualByComparingTo("60000");
		assertThat(structure.getVariablePay()).isEqualByComparingTo("6000");
		assertThat(structure.getBonuses()).isEqualByComparingTo("3000");
		assertThat(structure.getDeduction()).isEqualByComparingTo("2000");
		assertRowCounts(1, 1);
		try (var client = HttpClient.newHttpClient()) {
			var get = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/employees?dep_id=" + newDepartmentId))
					.timeout(Duration.ofSeconds(10))
					.header("Authorization", "Bearer " + jwtService.generateToken("tester")).GET().build();
			var retrieved = client.send(get, HttpResponse.BodyHandlers.ofString());
			assertThat(retrieved.statusCode()).isEqualTo(200);
			assertThat(objectMapper.readTree(retrieved.body()).get(0)).isEqualTo(result);
		}
	}

	@Test
	void updateEmployeeAcceptsMatchingSalaryRepeatedRequestsAndZeroComponents() throws Exception {
		var matching = request().put("salary", new BigDecimal("67000.00"));
		assertThat(put(employeeId.toString(), matching, true).statusCode()).isEqualTo(200);
		assertThat(put(employeeId.toString(), matching, true).statusCode()).isEqualTo(200);
		var zero = request().put("salary", BigDecimal.ZERO);
		for (String field : List.of("fixedPay", "variablePay", "bonuses", "deduction")) {
			((ObjectNode) zero.get("salaryStructureDto")).put(field, BigDecimal.ZERO);
		}
		assertThat(put(employeeId.toString(), zero, true).statusCode()).isEqualTo(200);
		assertThat(employeeRepository.findById(employeeId).orElseThrow().getSalary()).isZero();
		assertRowCounts(1, 1);
	}

	@Test
	void updateEmployeeDoesNotInsertWhenEmployeeIsMissing() throws Exception {
		assertThat(put(Integer.toString(Integer.MAX_VALUE), request(), true).statusCode()).isEqualTo(404);
		assertOriginalData();
	}

	@Test
	void updateEmployeeDoesNotCreateMissingSalaryStructure() throws Exception {
		salaryStructureRepository.deleteById(employeeId);

		assertThat(put(employeeId.toString(), request(), true).statusCode()).isEqualTo(404);
		assertThat(employeeRepository.findById(employeeId).orElseThrow().getName()).isEqualTo("Alice");
		assertThat(employeeRepository.findById(employeeId).orElseThrow().getSalary()).isEqualByComparingTo("56000");
		assertRowCounts(1, 0);
	}

	@Test
	void updateEmployeeRejectsMissingDepartmentWithoutChangingEitherRow() throws Exception {
		assertThat(put(employeeId.toString(), request().put("departmentId", Integer.MAX_VALUE), true).statusCode())
				.isEqualTo(404);
		assertOriginalData();
	}

	@Test
	void updateEmployeeRejectsIncompleteFieldsAndBodyIds() throws Exception {
		List<Consumer<ObjectNode>> invalidRequests = List.of(
				body -> body.put("id", employeeId),
				body -> ((ObjectNode) body.get("salaryStructureDto")).put("empId", employeeId),
				body -> body.remove("name"),
				body -> body.put("name", " "),
				body -> body.put("name", "a".repeat(256)),
				body -> body.remove("departmentId"),
				body -> body.put("departmentId", 0),
				body -> body.remove("country"),
				body -> body.put("country", " "),
				body -> body.put("country", "a".repeat(256)),
				body -> body.remove("salaryStructureDto"),
				body -> body.putNull("salaryStructureDto"));
		for (var mutation : invalidRequests) {
			var body = request();
			mutation.accept(body);
			assertThat(put(employeeId.toString(), body, true).statusCode()).as(body.toString()).isEqualTo(400);
			assertOriginalData();
		}
	}

	@Test
	void updateEmployeeRejectsInvalidMoneyAndInconsistentTotals() throws Exception {
		for (String field : List.of("fixedPay", "variablePay", "bonuses", "deduction")) {
			for (BigDecimal amount : new BigDecimal[] { null, new BigDecimal("-0.01"), new BigDecimal("1.001"),
					new BigDecimal("1" + "0".repeat(36)) }) {
				var body = request();
				((ObjectNode) body.get("salaryStructureDto")).put(field, amount);
				assertThat(put(employeeId.toString(), body, true).statusCode()).as(field + "=" + amount).isEqualTo(400);
				assertOriginalData();
			}
		}
		for (BigDecimal salary : List.of(new BigDecimal("-1"), new BigDecimal("67000.001"), BigDecimal.ONE)) {
			assertThat(put(employeeId.toString(), request().put("salary", salary), true).statusCode()).isEqualTo(400);
		}
		var negative = request();
		((ObjectNode) negative.get("salaryStructureDto")).put("deduction", new BigDecimal("69000.01"));
		assertThat(put(employeeId.toString(), negative, true).statusCode()).isEqualTo(400);
		var overflow = request();
		((ObjectNode) overflow.get("salaryStructureDto")).put("fixedPay", new BigDecimal("9".repeat(36)));
		assertThat(put(employeeId.toString(), overflow, true).statusCode()).isEqualTo(400);
		assertOriginalData();
	}

	@Test
	void updateEmployeeRequiresJwtAndValidPathId() throws Exception {
		assertThat(put(employeeId.toString(), request(), false).statusCode()).isEqualTo(401);
		for (String id : List.of("0", "-1", "abc")) {
			assertThat(put(id, request(), true).statusCode()).isEqualTo(400);
		}
		assertOriginalData();
	}

	@Test
	void databaseFailureRollsBackChangesToBothExistingRows() throws Exception {
		jdbcTemplate.execute("ALTER TABLE salary_structure ADD CONSTRAINT test_update_bonus CHECK (bonuses <> 4321.00)");
		try {
			var body = request();
			((ObjectNode) body.get("salaryStructureDto")).put("bonuses", new BigDecimal("4321.00"));
			assertThat(put(employeeId.toString(), body, true).statusCode()).isEqualTo(500);
			assertOriginalData();
		} finally {
			jdbcTemplate.execute("ALTER TABLE salary_structure DROP CONSTRAINT test_update_bonus");
		}
	}

	private void assertOriginalData() {
		var employee = employeeRepository.findById(employeeId).orElseThrow();
		assertThat(employee.getName()).isEqualTo("Alice");
		assertThat(employee.getCountry()).isEqualTo("India");
		assertThat(employee.getDepartmentId()).isEqualTo(departmentId);
		assertThat(employee.getSalary()).isEqualByComparingTo("56000");
		var structure = salaryStructureRepository.findById(employeeId).orElseThrow();
		assertThat(structure.getFixedPay()).isEqualByComparingTo("50000");
		assertThat(structure.getVariablePay()).isEqualByComparingTo("5000");
		assertThat(structure.getBonuses()).isEqualByComparingTo("2000");
		assertThat(structure.getDeduction()).isEqualByComparingTo("1000");
		assertRowCounts(1, 1);
	}

	private void assertRowCounts(long employees, long structures) {
		assertThat(employeeRepository.count()).isEqualTo(employees);
		assertThat(salaryStructureRepository.count()).isEqualTo(structures);
	}

	private ObjectNode request() {
		var body = objectMapper.createObjectNode().put("name", " Bob ")
				.put("departmentId", departmentId).put("country", " Canada ");
		body.putObject("salaryStructureDto").put("fixedPay", new BigDecimal("60000.00"))
				.put("variablePay", new BigDecimal("6000.00"))
				.put("bonuses", new BigDecimal("3000.00"))
				.put("deduction", new BigDecimal("2000.00"));
		return body;
	}

	private HttpResponse<String> put(String id, ObjectNode body, boolean authenticated) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/employees/" + id))
					.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
			if (authenticated) {
				builder.header("Authorization", "Bearer " + jwtService.generateToken("tester"));
			}
			return client.send(builder.PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
					.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

}
