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
		var total = rows.stream().map(SalaryDistributionProjection::getTotalSalary)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		var groups = rows.stream().map(row -> new SalaryDistributionResponseDto.Group(
				row.getGroupKey(), row.getLabel(), row.getTotalSalary(), row.getEmployeeCount(),
				row.getMissingSalaryCount(), total.signum() == 0 ? BigDecimal.ZERO :
				row.getTotalSalary().multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)))
				.toList();
		return new SalaryDistributionResponseDto(groupBy, total,
				rows.stream().mapToLong(SalaryDistributionProjection::getEmployeeCount).sum(),
				rows.stream().mapToLong(SalaryDistributionProjection::getMissingSalaryCount).sum(), groups);
	}

}
