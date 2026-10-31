package org.project.sms.service;

import java.util.List;

import org.project.sms.dto.EmployeeDto;
import org.project.sms.entity.Employee;
import org.project.sms.repository.EmployeeRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeService {

	private final EmployeeRepository employeeRepository;
	private final SalaryStructureService salaryStructureService;

	@Transactional(readOnly = true)
	public List<EmployeeDto> getEmp(Integer depId, Pageable pagination) {
		return employeeRepository.findByDepartmentId(depId, pagination)
				.map(this::toDto)
				.getContent();
	}

	private EmployeeDto toDto(Employee employee) {
		return new EmployeeDto(employee.getId(), employee.getName(), employee.getDepartmentId(),
				employee.getSalary(), employee.getCountry(),
				salaryStructureService.getSalaryStructure(employee.getId()));
	}

}
