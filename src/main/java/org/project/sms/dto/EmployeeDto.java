package org.project.sms.dto;

import java.math.BigDecimal;

import lombok.Value;

@Value
public class EmployeeDto {

	Integer id;

	String name;

	Integer departmentId;

	BigDecimal salary;

	String country;

	SalaryStructureDto salaryStructureDto;

}
