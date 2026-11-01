package org.project.sms.repository;

import java.math.BigDecimal;

public interface SalaryDistributionProjection {

	String getGroupKey();
	String getLabel();
	BigDecimal getSalarySum();
	long getEmployeeCount();
	long getMissingSalaryCount();

}
