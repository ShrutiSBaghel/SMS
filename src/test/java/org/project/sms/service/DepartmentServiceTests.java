package org.project.sms.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.sms.entity.Department;
import org.project.sms.repository.DepartmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTests {

	@Mock
	private DepartmentRepository departmentRepository;

	@InjectMocks
	private DepartmentService departmentService;

	@Test
	void getAllDepMapsEveryDepartmentWithZeroEmployees() {
		var engineering = new Department();
		engineering.setId(1);
		engineering.setName("Engineering");
		var finance = new Department();
		finance.setId(2);
		finance.setName("Finance");
		when(departmentRepository.findAll()).thenReturn(List.of(engineering, finance));

		var result = departmentService.getAllDep();

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getId()).isEqualTo(1);
		assertThat(result.get(0).getName()).isEqualTo("Engineering");
		assertThat(result.get(1).getId()).isEqualTo(2);
		assertThat(result.get(1).getName()).isEqualTo("Finance");
		assertThat(result).allSatisfy(department -> assertThat(department.getTotalEmployees()).isZero());
		verify(departmentRepository).findAll();
	}

	@Test
	void getAllDepReturnsEmptyListWhenNoDepartmentsExist() {
		when(departmentRepository.findAll()).thenReturn(List.of());

		assertThat(departmentService.getAllDep()).isEmpty();
		verify(departmentRepository).findAll();
	}

}
