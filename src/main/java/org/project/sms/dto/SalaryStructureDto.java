package org.project.sms.dto;

import java.math.BigDecimal;

import lombok.Value;

@Value
public class SalaryStructureDto {

	Integer empId;

	BigDecimal fixedPay;

	BigDecimal variablePay;

	BigDecimal bonuses;

	BigDecimal deduction;

}
