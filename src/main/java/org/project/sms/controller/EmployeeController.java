package org.project.sms.controller;

import java.util.List;

import org.project.sms.dto.EmployeeDto;
import org.project.sms.dto.EmployeeDeleteResponseDto;
import org.project.sms.service.EmployeeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

	private final EmployeeService employeeService;

	@PostMapping
	public ResponseEntity<EmployeeDto> createEmployee(@Valid @RequestBody EmployeeDto request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.createEmployee(request));
	}

	@PutMapping("/{id}")
	public EmployeeDto updateEmployee(@PathVariable("id") Integer id, @Valid @RequestBody EmployeeDto request) {
		return employeeService.updateEmployee(id, request);
	}

	@DeleteMapping("/{id}")
	public EmployeeDeleteResponseDto deleteEmployee(@PathVariable("id") Integer id) {
		return employeeService.deleteEmployee(id);
	}

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

}
