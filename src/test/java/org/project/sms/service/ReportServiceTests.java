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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTests {

	@Mock private EmployeeRepository employees;
	@InjectMocks private ReportService reports;

	@Test
	void computesGroupAveragesAndWeightsTheOverallAverageByRecordedSalaries() {
		when(employees.salaryByDepartment()).thenReturn(List.of(row("150.30", 3, 1), row("49.70", 1, 0)));
		var report = reports.salaryDistribution("department");
		assertThat(report.getAverageSalary()).isEqualByComparingTo("66.67");
		assertThat(report.getEmployeeCount()).isEqualTo(4);
		assertThat(report.getMissingSalaryCount()).isEqualTo(1);
		assertThat(report.getGroups().get(0).getAverageSalary()).isEqualByComparingTo("75.15");
		assertThat(report.getGroups().get(1).getAverageSalary()).isEqualByComparingTo("49.70");
	}

	@Test
	void distinguishesMissingSalariesFromRecordedZeroSalaries() {
		when(employees.salaryByCountry()).thenReturn(List.of());
		assertThat(reports.salaryDistribution("country").getGroups()).isEmpty();
		assertThat(reports.salaryDistribution("country").getAverageSalary()).isNull();
		when(employees.salaryByCountry()).thenReturn(List.of(row("0", 1, 1)));
		assertThat(reports.salaryDistribution("country").getAverageSalary()).isNull();
		assertThat(reports.salaryDistribution("country").getGroups().get(0).getAverageSalary()).isNull();
		when(employees.salaryByCountry()).thenReturn(List.of(row("0", 1, 0)));
		assertThat(reports.salaryDistribution("country").getAverageSalary()).isZero();
		assertThat(reports.salaryDistribution("country").getGroups().get(0).getAverageSalary()).isZero();
	}

	@Test
	void roundsOnlyAfterDividingTheUnroundedSalarySum() {
		when(employees.salaryByCountry()).thenReturn(List.of(row("100", 3, 0), row("0.02", 1, 0)));
		var report = reports.salaryDistribution("country");
		assertThat(report.getGroups().get(0).getAverageSalary()).isEqualByComparingTo("33.33");
		assertThat(report.getAverageSalary()).isEqualByComparingTo("25.01");
	}

	@Test
	void rejectsUnsupportedGroupingBeforeQuerying() {
		assertThatThrownBy(() -> reports.salaryDistribution("role")).isInstanceOf(ResponseStatusException.class);
		verifyNoInteractions(employees);
	}

	private SalaryDistributionProjection row(String total, long count, long missing) {
		return new SalaryRow("group", "Group", new BigDecimal(total), count, missing);
	}

	@lombok.Value
	private static class SalaryRow implements SalaryDistributionProjection {
		String groupKey;
		String label;
		BigDecimal salarySum;
		long employeeCount;
		long missingSalaryCount;
	}

}
