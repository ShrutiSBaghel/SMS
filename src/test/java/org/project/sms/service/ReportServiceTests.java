package org.project.sms.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.sms.repository.EmployeeRepository;
import org.project.sms.repository.SalaryDistributionProjection;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTests {

	@Mock private EmployeeRepository employees;
	@InjectMocks private ReportService reports;

	@Test
	void sumsSalaryPreciselyAndCalculatesPercentages() {
		when(employees.salaryByDepartment()).thenReturn(List.of(row("150.30", 3, 1), row("49.70", 1, 0)));
		var report = reports.salaryDistribution("department");
		assertThat(report.getTotalSalary()).isEqualByComparingTo("200.00");
		assertThat(report.getEmployeeCount()).isEqualTo(4);
		assertThat(report.getMissingSalaryCount()).isEqualTo(1);
		assertThat(report.getGroups().get(0).getPercentage()).isEqualByComparingTo("75.15");
	}

	@Test
	void supportsEmptyAndZeroCountryTotals() {
		when(employees.salaryByCountry()).thenReturn(List.of());
		assertThat(reports.salaryDistribution("country").getGroups()).isEmpty();
		when(employees.salaryByCountry()).thenReturn(List.of(row("0", 1, 1)));
		assertThat(reports.salaryDistribution("country").getGroups().get(0).getPercentage()).isZero();
	}

	@Test
	void rejectsUnsupportedGroupingBeforeQuerying() {
		assertThatThrownBy(() -> reports.salaryDistribution("role")).isInstanceOf(ResponseStatusException.class);
		verifyNoInteractions(employees);
	}

	private SalaryDistributionProjection row(String total, long count, long missing) {
		var row = mock(SalaryDistributionProjection.class);
		when(row.getTotalSalary()).thenReturn(new BigDecimal(total));
		when(row.getEmployeeCount()).thenReturn(count);
		when(row.getMissingSalaryCount()).thenReturn(missing);
		return row;
	}

}
