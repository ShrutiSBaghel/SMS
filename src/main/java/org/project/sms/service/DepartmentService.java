package org.project.sms.service;

import org.project.sms.dto.DepartmentDto;
import org.project.sms.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

	private final DepartmentRepository departmentRepository;

	@Transactional(readOnly = true)
	public List<DepartmentDto> getAllDep() {
		return departmentRepository.findAll().stream()
				.map(department -> new DepartmentDto(
						department.getId(),
						department.getName(),
						// TODO: Query the employee count per department when the employee table is added.
						0L))
				.toList();
	}

}
