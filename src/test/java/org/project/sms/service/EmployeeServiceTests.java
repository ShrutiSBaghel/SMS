package org.project.sms.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.sms.dto.SalaryStructureDto;
import org.project.sms.entity.Employee;
import org.project.sms.repository.EmployeeRepository;
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

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTests {

	@Mock
	private EmployeeRepository employeeRepository;

	@Mock
	private SalaryStructureService salaryStructureService;

	@InjectMocks
	private EmployeeService employeeService;

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
