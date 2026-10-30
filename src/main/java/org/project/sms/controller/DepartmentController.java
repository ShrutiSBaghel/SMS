package org.project.sms.controller;

import org.project.sms.dto.DepartmentDto;
import org.project.sms.service.DepartmentService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@CrossOrigin(value = "http://localhost:4200")
public class DepartmentController {

	private final DepartmentService departmentService;

	@GetMapping
	public List<DepartmentDto> getAllDep() {
		return departmentService.getAllDep();
	}

}
