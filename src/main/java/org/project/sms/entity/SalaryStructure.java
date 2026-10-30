package org.project.sms.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SalaryStructure {

	@Id
	@Column(name = "emp_id")
	private Integer empId;

	@Column(name = "fixed_pay", nullable = false)
	private BigDecimal fixedPay;

	@Column(name = "variable_pay", nullable = false)
	private BigDecimal variablePay;

	@Column(nullable = false)
	private BigDecimal bonuses;

	@Column(nullable = false)
	private BigDecimal deduction;

}
