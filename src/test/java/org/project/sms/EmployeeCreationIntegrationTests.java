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
class EmployeeCreationIntegrationTests {

	private final EmployeeRepository employeeRepository;

	private final SalaryStructureRepository salaryStructureRepository;

	private final DepartmentRepository departmentRepository;

	private final JwtService jwtService;

	private final ObjectMapper objectMapper;

	private final JdbcTemplate jdbcTemplate;

	private Integer departmentId;

	@LocalServerPort
	private int port;

	@Autowired
	EmployeeCreationIntegrationTests(EmployeeRepository employeeRepository,
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
	void prepareDepartment() {
		salaryStructureRepository.deleteAll();
		employeeRepository.deleteAll();
		departmentRepository.deleteAll();
		var department = new Department();
		department.setName("Engineering");
		departmentId = departmentRepository.saveAndFlush(department).getId();
	}

	@Test
	void createEmployeePersistsBothRecordsAndReturnsGeneratedIdsAndCalculatedSalary() throws Exception {
		var response = post(request(), true);

		assertThat(response.statusCode()).isEqualTo(201);
		var result = objectMapper.readTree(response.body());
		int employeeId = result.get("id").asInt();
		assertThat(employeeId).isPositive();
		assertThat(result.get("name").asText()).isEqualTo("Alice");
		assertThat(result.get("country").asText()).isEqualTo("India");
		assertThat(result.get("departmentId").asInt()).isEqualTo(departmentId);
		assertThat(result.get("salary").decimalValue()).isEqualByComparingTo("56000.00");
		assertThat(result.get("salaryStructureDto").get("empId").asInt()).isEqualTo(employeeId);
		assertThat(employeeRepository.count()).isEqualTo(1);
		assertThat(salaryStructureRepository.count()).isEqualTo(1);
		var employee = employeeRepository.findById(employeeId).orElseThrow();
		assertThat(employee.getSalary()).isEqualByComparingTo("56000.00");
		assertThat(employee.getDepartmentId()).isEqualTo(departmentId);
		var structure = salaryStructureRepository.findById(employeeId).orElseThrow();
		assertThat(structure.getFixedPay()).isEqualByComparingTo("50000.00");
		assertThat(structure.getVariablePay()).isEqualByComparingTo("5000.00");
		assertThat(structure.getBonuses()).isEqualByComparingTo("2000.00");
		assertThat(structure.getDeduction()).isEqualByComparingTo("1000.00");

		try (var client = HttpClient.newHttpClient()) {
			var get = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/employees?dep_id=" + departmentId))
					.timeout(Duration.ofSeconds(10))
					.header("Authorization", "Bearer " + jwtService.generateToken("tester"))
					.GET().build();
			var retrieved = client.send(get, HttpResponse.BodyHandlers.ofString());
			assertThat(retrieved.statusCode()).isEqualTo(200);
			var employees = objectMapper.readTree(retrieved.body());
			assertThat(employees.size()).isEqualTo(1);
			assertThat(employees.get(0)).isEqualTo(result);
		}
	}

	@Test
	void createEmployeeAcceptsMatchingSalaryAndZeroComponents() throws Exception {
		var matching = request().put("salary", new BigDecimal("56000"));
		assertThat(post(matching, true).statusCode()).isEqualTo(201);
		var zero = request().put("salary", BigDecimal.ZERO);
		for (var field : List.of("fixedPay", "variablePay", "bonuses", "deduction")) {
			((ObjectNode) zero.get("salaryStructureDto")).put(field, BigDecimal.ZERO);
		}
		var response = post(zero, true);
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(objectMapper.readTree(response.body()).get("salary").decimalValue()).isZero();
	}

	@Test
	void createEmployeeRejectsUnknownDepartmentWithoutPartialWrites() throws Exception {
		assertThat(post(request().put("departmentId", Integer.MAX_VALUE), true).statusCode()).isEqualTo(404);
		assertEmptyEmployeeTables();
	}

	@Test
	void createEmployeeRejectsInvalidFieldsAndClientSuppliedIds() throws Exception {
		List<Consumer<ObjectNode>> invalidRequests = List.of(
				body -> body.put("id", 100),
				body -> ((ObjectNode) body.get("salaryStructureDto")).put("empId", 100),
				body -> body.remove("name"),
				body -> body.put("name", " "),
				body -> body.put("name", "a".repeat(256)),
				body -> body.remove("departmentId"),
				body -> body.put("departmentId", 0),
				body -> body.remove("country"),
				body -> body.put("country", " "),
				body -> body.put("country", "a".repeat(256)),
				body -> body.remove("salaryStructureDto"),
				body -> body.putNull("salaryStructureDto"),
				body -> body.put("salary", new BigDecimal("56000.001")),
				body -> body.put("salary", BigDecimal.ONE.negate()));
		for (var mutation : invalidRequests) {
			var body = request();
			mutation.accept(body);
			assertThat(post(body, true).statusCode()).as(body.toString()).isEqualTo(400);
			assertEmptyEmployeeTables();
		}
	}

	@Test
	void createEmployeeValidatesEachMoneyComponentAndSalaryConsistency() throws Exception {
		for (var field : List.of("fixedPay", "variablePay", "bonuses", "deduction")) {
			for (BigDecimal amount : new BigDecimal[] { null, new BigDecimal("-0.01"),
					new BigDecimal("1.001"), new BigDecimal("1" + "0".repeat(36)) }) {
				var body = request();
				((ObjectNode) body.get("salaryStructureDto")).put(field, amount);
				assertThat(post(body, true).statusCode()).as(field + "=" + amount).isEqualTo(400);
				assertEmptyEmployeeTables();
			}
		}
		assertThat(post(request().put("salary", new BigDecimal("56000.01")), true).statusCode()).isEqualTo(400);
		var negativeTotal = request();
		((ObjectNode) negativeTotal.get("salaryStructureDto")).put("deduction", new BigDecimal("57000.01"));
		assertThat(post(negativeTotal, true).statusCode()).isEqualTo(400);
		var overflow = request();
		((ObjectNode) overflow.get("salaryStructureDto")).put("fixedPay", new BigDecimal("9".repeat(36)));
		assertThat(post(overflow, true).statusCode()).isEqualTo(400);
		assertEmptyEmployeeTables();
	}

	@Test
	void createEmployeeCannotOverwriteExistingEmployee() throws Exception {
		var created = objectMapper.readTree(post(request(), true).body());
		int id = created.get("id").asInt();
		var overwrite = request().put("id", id).put("name", "Overwrite");
		((ObjectNode) overwrite.get("salaryStructureDto")).put("empId", id);

		assertThat(post(overwrite, true).statusCode()).isEqualTo(400);
		assertThat(employeeRepository.count()).isEqualTo(1);
		assertThat(employeeRepository.findById(id).orElseThrow().getName()).isEqualTo("Alice");
	}

	@Test
	void createEmployeeRequiresJwtAuthentication() throws Exception {
		assertThat(post(request(), false).statusCode()).isEqualTo(401);
		assertEmptyEmployeeTables();
	}

	@Test
	void salaryStructureDatabaseFailureRollsBackEmployeeInsert() throws Exception {
		// Force a real failure at flush/commit, after the IDENTITY employee insert.
		jdbcTemplate.execute("ALTER TABLE salary_structure ADD CONSTRAINT test_reject_bonus CHECK (bonuses <> 4321.00)");
		try {
			var body = request();
			((ObjectNode) body.get("salaryStructureDto")).put("bonuses", new BigDecimal("4321.00"));
			assertThat(post(body, true).statusCode()).isEqualTo(500);
			assertEmptyEmployeeTables();
		} finally {
			jdbcTemplate.execute("ALTER TABLE salary_structure DROP CONSTRAINT test_reject_bonus");
		}
	}

	private void assertEmptyEmployeeTables() {
		assertThat(employeeRepository.count()).isZero();
		assertThat(salaryStructureRepository.count()).isZero();
	}

	private ObjectNode request() {
		var body = objectMapper.createObjectNode().put("name", " Alice ")
				.put("departmentId", departmentId).put("country", " India ");
		body.putObject("salaryStructureDto")
				.put("fixedPay", new BigDecimal("50000.00"))
				.put("variablePay", new BigDecimal("5000.00"))
				.put("bonuses", new BigDecimal("2000.00"))
				.put("deduction", new BigDecimal("1000.00"));
		return body;
	}

	private HttpResponse<String> post(ObjectNode body, boolean authenticated) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/employees"))
					.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
			if (authenticated) {
				builder.header("Authorization", "Bearer " + jwtService.generateToken("tester"));
			}
			return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
					.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

}
