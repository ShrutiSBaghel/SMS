package org.project.sms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.project.sms.dto.SalaryDistributionResponseDto;
import org.project.sms.repository.EmployeeRepository;
import org.project.sms.repository.SalaryDistributionProjection;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {

	private final EmployeeRepository employeeRepository;

	@Transactional(readOnly = true)
	public SalaryDistributionResponseDto salaryDistribution(String groupBy) {
		var rows = switch (groupBy) {
			case "department" -> employeeRepository.salaryByDepartment();
			case "country" -> employeeRepository.salaryByCountry();
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"groupBy must be department or country");
		};
		var salarySum = rows.stream().map(SalaryDistributionProjection::getSalarySum)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		long employeeCount = rows.stream().mapToLong(SalaryDistributionProjection::getEmployeeCount).sum();
		long missingSalaryCount = rows.stream().mapToLong(SalaryDistributionProjection::getMissingSalaryCount).sum();
		var groups = rows.stream().map(row -> new SalaryDistributionResponseDto.Group(
				row.getGroupKey(), row.getLabel(),
				average(row.getSalarySum(), row.getEmployeeCount() - row.getMissingSalaryCount()),
				row.getEmployeeCount(), row.getMissingSalaryCount()))
				.toList();
		return new SalaryDistributionResponseDto(groupBy, average(salarySum, employeeCount - missingSalaryCount),
				employeeCount, missingSalaryCount, groups);
	}

	private BigDecimal average(BigDecimal salarySum, long salaryCount) {
		// Missing salaries are unknown, whereas a recorded zero is part of the average.
		return salaryCount == 0 ? null : salarySum.divide(BigDecimal.valueOf(salaryCount), 2, RoundingMode.HALF_UP);
	}

}
