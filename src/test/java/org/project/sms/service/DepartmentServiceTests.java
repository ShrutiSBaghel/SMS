package org.project.sms.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.sms.entity.Department;
import org.project.sms.repository.DepartmentRepository;
import org.project.sms.repository.EmployeeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTests {

	@Mock
	private DepartmentRepository departmentRepository;

	@Mock
	private EmployeeRepository employeeRepository;

	@InjectMocks
	private DepartmentService departmentService;

	@Test
	void getAllDepMapsEveryDepartmentWithItsEmployeeCount() {
		var engineering = new Department();
		engineering.setId(1);
		engineering.setName("Engineering");
		var finance = new Department();
		finance.setId(2);
		finance.setName("Finance");
		when(departmentRepository.findAll()).thenReturn(List.of(engineering, finance));
		when(employeeRepository.countByDepartmentId(1)).thenReturn(12L);
		when(employeeRepository.countByDepartmentId(2)).thenReturn(0L);

		var result = departmentService.getAllDep();

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getId()).isEqualTo(1);
		assertThat(result.get(0).getName()).isEqualTo("Engineering");
		assertThat(result.get(0).getTotalEmployees()).isEqualTo(12);
		assertThat(result.get(1).getId()).isEqualTo(2);
		assertThat(result.get(1).getName()).isEqualTo("Finance");
		assertThat(result.get(1).getTotalEmployees()).isZero();
		verify(departmentRepository).findAll();
	}

	@Test
	void getAllDepReturnsEmptyListWhenNoDepartmentsExist() {
		when(departmentRepository.findAll()).thenReturn(List.of());

		assertThat(departmentService.getAllDep()).isEmpty();
		verify(departmentRepository).findAll();
		verifyNoInteractions(employeeRepository);
	}

}
