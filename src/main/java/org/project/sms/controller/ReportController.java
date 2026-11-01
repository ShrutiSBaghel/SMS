package org.project.sms.controller;

import org.project.sms.dto.SalaryDistributionResponseDto;
import org.project.sms.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

	private final ReportService reportService;

	@GetMapping("/salary-distribution")
	public SalaryDistributionResponseDto salaryDistribution(
			@RequestParam(defaultValue = "department") String groupBy) {
		return reportService.salaryDistribution(groupBy);
	}

}
