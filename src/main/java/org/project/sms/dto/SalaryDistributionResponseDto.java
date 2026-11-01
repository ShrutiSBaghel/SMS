package org.project.sms.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Value;

@Value
public class SalaryDistributionResponseDto {

	String groupBy;
	BigDecimal averageSalary;
	long employeeCount;
	long missingSalaryCount;
	List<Group> groups;

	@Value
	public static class Group {
		String key;
		String label;
		BigDecimal averageSalary;
		long employeeCount;
		long missingSalaryCount;
	}

}
