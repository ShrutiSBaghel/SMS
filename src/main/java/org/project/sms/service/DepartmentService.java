package org.project.sms.service;

import org.project.sms.dto.DepartmentDto;
import org.project.sms.repository.DepartmentRepository;
import org.project.sms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

	private final DepartmentRepository departmentRepository;
	private final EmployeeRepository employeeRepository;

	@Transactional(readOnly = true)
	public List<DepartmentDto> getAllDep() {
		return departmentRepository.findAll().stream()
				.map(department -> new DepartmentDto(
						department.getId(),
						department.getName(),
						employeeRepository.countByDepartmentId(department.getId())))
				.toList();
	}

}
