package org.project.sms.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.project.sms.dto.EmployeeDto;
import org.project.sms.dto.SalaryStructureDto;
import org.project.sms.entity.Employee;
import org.project.sms.entity.SalaryStructure;
import org.project.sms.repository.DepartmentRepository;
import org.project.sms.repository.EmployeeRepository;
import org.project.sms.repository.SalaryStructureRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTests {

	@Mock
	private EmployeeRepository employeeRepository;

	@Mock
	private SalaryStructureService salaryStructureService;

	@Mock
	private DepartmentRepository departmentRepository;

	@Mock
	private SalaryStructureRepository salaryStructureRepository;

	@InjectMocks
	private EmployeeService employeeService;

	@Test
	void deleteEmployeeRemovesSalaryStructureBeforeEmployee() {
		var employee = employee();
		when(employeeRepository.findById(1)).thenReturn(Optional.of(employee));
		var result = employeeService.deleteEmployee(1);
		var order = inOrder(employeeRepository, salaryStructureRepository);
		order.verify(employeeRepository).findById(1);
		order.verify(salaryStructureRepository).deleteById(1);
		order.verify(salaryStructureRepository).flush();
		order.verify(employeeRepository).delete(employee);
		assertThat(result.getId()).isEqualTo(1);
		assertThat(result.getMessage()).isEqualTo("Employee Deleted Successfully");
		verifyNoInteractions(departmentRepository, salaryStructureService);
	}

	@Test
	void deleteEmployeeRejectsInvalidIdBeforeRepositoryAccess() {
		for (Integer id : new Integer[] { null, 0, -1 }) {
			assertThatThrownBy(() -> employeeService.deleteEmployee(id))
					.isInstanceOfSatisfying(ResponseStatusException.class,
							exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		}
		verifyNoInteractions(employeeRepository, salaryStructureRepository);
	}

	@Test
	void deleteEmployeeReturnsNotFoundWithoutDeletingOtherRecords() {
		assertThatThrownBy(() -> employeeService.deleteEmployee(999))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		verifyNoInteractions(salaryStructureRepository);
		verify(employeeRepository, never()).delete(any());
	}

	@Test
	void deleteEmployeeDoesNotDeleteEmployeeWhenSalaryDeletionFails() {
		when(employeeRepository.findById(1)).thenReturn(Optional.of(employee()));
		doThrow(new org.springframework.dao.DataIntegrityViolationException("Blocked"))
				.when(salaryStructureRepository).flush();
		assertThatThrownBy(() -> employeeService.deleteEmployee(1))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		verify(employeeRepository, never()).delete(any());
	}

	@Test
	void updateEmployeeChangesLoadedEntitiesWithoutSavingNewRowsOrChangingIds() {
		var employee = employee();
		var salaryStructure = salaryStructure();
		when(employeeRepository.findById(1)).thenReturn(Optional.of(employee));
		when(salaryStructureRepository.findById(1)).thenReturn(Optional.of(salaryStructure));
		when(departmentRepository.existsById(7)).thenReturn(true);
		var request = new EmployeeDto(null, " Bob ", 7, null, " Canada ",
				new SalaryStructureDto(null, new BigDecimal("60000"), new BigDecimal("6000"),
						new BigDecimal("3000"), new BigDecimal("2000")));

		var result = employeeService.updateEmployee(1, request);

		assertThat(result.getId()).isEqualTo(1);
		assertThat(result.getSalaryStructureDto().getEmpId()).isEqualTo(1);
		assertThat(employee.getName()).isEqualTo("Bob");
		assertThat(employee.getCountry()).isEqualTo("Canada");
		assertThat(employee.getSalary()).isEqualByComparingTo("67000");
		assertThat(salaryStructure.getFixedPay()).isEqualByComparingTo("60000");
		assertThat(salaryStructure.getBonuses()).isEqualByComparingTo("3000");
		verify(employeeRepository, never()).save(any());
		verify(salaryStructureRepository, never()).save(any());
	}

	@Test
	void updateEmployeeRejectsMissingEmployeeBeforeLookingUpOtherRecords() {
		assertThatThrownBy(() -> employeeService.updateEmployee(1, createRequest(null, structure())))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		verifyNoInteractions(salaryStructureRepository, departmentRepository);
		verify(employeeRepository, never()).save(any());
	}

	@Test
	void updateEmployeeRejectsMissingStructureWithoutChangingEmployee() {
		var employee = employee();
		when(employeeRepository.findById(1)).thenReturn(Optional.of(employee));

		assertThatThrownBy(() -> employeeService.updateEmployee(1, createRequest(null, structure())))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		assertThat(employee.getSalary()).isEqualByComparingTo("50000");
		verifyNoInteractions(departmentRepository);
		verify(salaryStructureRepository, never()).save(any());
	}

	@Test
	void updateEmployeeChecksSalaryBeforeMutatingEitherRecord() {
		var employee = employee();
		var salaryStructure = salaryStructure();
		when(employeeRepository.findById(1)).thenReturn(Optional.of(employee));
		when(salaryStructureRepository.findById(1)).thenReturn(Optional.of(salaryStructure));
		when(departmentRepository.existsById(7)).thenReturn(true);

		assertThatThrownBy(() -> employeeService.updateEmployee(1, createRequest(BigDecimal.ONE, structure())))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		assertThat(employee.getSalary()).isEqualByComparingTo("50000");
		assertThat(salaryStructure.getFixedPay()).isEqualByComparingTo("50000");
		verify(employeeRepository, never()).save(any());
		verify(salaryStructureRepository, never()).save(any());
	}

	@Test
	void updateEmployeeRejectsNonpositiveIdBeforeRepositoryAccess() {
		for (Integer id : new Integer[] { null, 0, -1 }) {
			assertThatThrownBy(() -> employeeService.updateEmployee(id, createRequest(null, structure())))
					.isInstanceOfSatisfying(ResponseStatusException.class,
							exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		}
		verifyNoInteractions(employeeRepository, salaryStructureRepository, departmentRepository);
	}

	private SalaryStructure salaryStructure() {
		var structure = new SalaryStructure();
		structure.setEmpId(1);
		structure.setFixedPay(new BigDecimal("50000"));
		structure.setVariablePay(BigDecimal.ZERO);
		structure.setBonuses(BigDecimal.ZERO);
		structure.setDeduction(BigDecimal.ZERO);
		return structure;
	}

	@Test
	void createEmployeeDerivesSalaryAndLinksStructureToGeneratedId() {
		when(departmentRepository.existsById(7)).thenReturn(true);
		when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
			Employee saved = invocation.getArgument(0);
			saved.setId(42);
			return saved;
		});

		var result = employeeService.createEmployee(createRequest(null,
				new SalaryStructureDto(null, new BigDecimal("50000"), new BigDecimal("5000"),
						new BigDecimal("2000"), new BigDecimal("1000"))));

		assertThat(result.getId()).isEqualTo(42);
		assertThat(result.getSalary()).isEqualByComparingTo("56000");
		assertThat(result.getName()).isEqualTo("Alice");
		assertThat(result.getCountry()).isEqualTo("India");
		assertThat(result.getSalaryStructureDto().getEmpId()).isEqualTo(42);
		var employeeCaptor = ArgumentCaptor.forClass(Employee.class);
		verify(employeeRepository).save(employeeCaptor.capture());
		assertThat(employeeCaptor.getValue().getDepartmentId()).isEqualTo(7);
		assertThat(employeeCaptor.getValue().getSalary()).isEqualByComparingTo("56000");
		var structureCaptor = ArgumentCaptor.forClass(SalaryStructure.class);
		verify(salaryStructureRepository).save(structureCaptor.capture());
		assertThat(structureCaptor.getValue().getEmpId()).isEqualTo(42);
		assertThat(structureCaptor.getValue().getFixedPay()).isEqualByComparingTo("50000");
		verifyNoInteractions(salaryStructureService);
	}

	@Test
	void createEmployeeRejectsMissingDepartmentWithoutSaving() {
		assertThatThrownBy(() -> employeeService.createEmployee(createRequest(null, structure())))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
		verifyNoInteractions(employeeRepository, salaryStructureRepository);
	}

	@Test
	void createEmployeeRejectsMismatchedSalaryAndNegativeOrOverflowingTotals() {
		when(departmentRepository.existsById(7)).thenReturn(true);
		var requests = List.of(
				createRequest(new BigDecimal("1"), structure()),
				createRequest(null, new SalaryStructureDto(null, BigDecimal.ZERO, BigDecimal.ZERO,
						BigDecimal.ZERO, BigDecimal.ONE)),
				createRequest(null, new SalaryStructureDto(null, new BigDecimal("9".repeat(36)),
						BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)));
		for (var request : requests) {
			assertThatThrownBy(() -> employeeService.createEmployee(request))
					.isInstanceOfSatisfying(ResponseStatusException.class,
							exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
		}
		verifyNoInteractions(employeeRepository, salaryStructureRepository);
	}

	private EmployeeDto createRequest(BigDecimal salary, SalaryStructureDto structure) {
		return new EmployeeDto(null, " Alice ", 7, salary, " India ", structure);
	}

	private SalaryStructureDto structure() {
		return new SalaryStructureDto(null, new BigDecimal("50000"), BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO);
	}

	@Test
	void getEmpPassesDepartmentAndPageableToRepositoryAndReturnsEmptyPage() {
		var pageable = PageRequest.of(3, 20, Sort.by("id"));
		when(employeeRepository.findByDepartmentId(7, pageable)).thenReturn(new SliceImpl<>(List.of()));

		assertThat(employeeService.getEmp(7, pageable)).isEmpty();
		verify(employeeRepository).findByDepartmentId(7, pageable);
		verifyNoInteractions(salaryStructureService);
	}

	private Employee employee() {
		var employee = new Employee();
		employee.setId(1);
		employee.setName("Alice");
		employee.setDepartmentId(7);
		employee.setSalary(new BigDecimal("50000"));
		employee.setCountry("India");
		return employee;
	}

}
