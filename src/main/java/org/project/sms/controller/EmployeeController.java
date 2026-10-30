package org.project.sms.controller;

import java.util.List;

import org.project.sms.dto.EmployeeDto;
import org.project.sms.dto.EmployeeFilterRequest;
import org.project.sms.service.EmployeeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
@CrossOrigin(value = "http://localhost:4200")
public class EmployeeController {

	private final EmployeeService employeeService;

	@GetMapping
	public List<EmployeeDto> getEmp(@RequestParam("dep_id") Integer depId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Page must be non-negative and size must be between 1 and 100");
		}

		return employeeService.getEmp(depId, PageRequest.of(page, size, Sort.by("id")));
	}

	@GetMapping("/filter")
	public List<EmployeeDto> filterEmployee(@ModelAttribute EmployeeFilterRequest request) {
		return employeeService.filterEmployee(request);
	}

}
