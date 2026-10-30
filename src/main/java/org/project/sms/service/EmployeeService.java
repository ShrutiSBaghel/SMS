package org.project.sms.service;

import java.util.List;

import org.project.sms.dto.EmployeeDto;
import org.project.sms.dto.EmployeeFilterRequest;
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

	@Transactional(readOnly = true)
	public List<EmployeeDto> getEmp(Integer depId, Pageable pagination) {
		return employeeRepository.findByDepartmentId(depId, pagination)
				.map(employee -> new EmployeeDto(
						employee.getId(),
						employee.getName(),
						employee.getDepartmentId(),
						employee.getSalary(),
						employee.getCountry()))
				.getContent();
	}

	public List<EmployeeDto> filterEmployee(EmployeeFilterRequest request) {
		// TODO: Delegate to the repository and map results once filtering is implemented.
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Employee filtering is not implemented yet");
	}

}
