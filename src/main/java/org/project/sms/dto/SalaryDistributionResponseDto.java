package org.project.sms.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Value;

@Value
public class SalaryDistributionResponseDto {

	String groupBy;
	BigDecimal totalSalary;
	long employeeCount;
	long missingSalaryCount;
	List<Group> groups;

	@Value
	public static class Group {
		String key;
		String label;
		BigDecimal totalSalary;
		long employeeCount;
		long missingSalaryCount;
		BigDecimal percentage;
	}

}
