package org.project.sms.controller;

import org.project.sms.dto.SalaryStructureDto;
import org.project.sms.service.SalaryStructureService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/salary-structures")
@RequiredArgsConstructor
@CrossOrigin(value = "http://localhost:4200")
public class SalaryStructureController {

	private final SalaryStructureService salaryStructureService;

	@GetMapping
	public SalaryStructureDto getSalaryStructure(@RequestParam("emp_id") Integer empId) {
		return salaryStructureService.getSalaryStructure(empId);
	}

}
