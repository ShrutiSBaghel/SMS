package org.project.sms.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.sms.entity.SalaryStructure;
import org.project.sms.repository.SalaryStructureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryStructureServiceTests {

	@Mock
	private SalaryStructureRepository salaryStructureRepository;

	@InjectMocks
	private SalaryStructureService salaryStructureService;

	@Test
	void calculateSalaryAddsFixedVariableAndBonusesAndSubtractsDeduction() {
		var structure = new SalaryStructure();
		structure.setEmpId(1);
		structure.setFixedPay(new BigDecimal("10000.10"));
		structure.setVariablePay(new BigDecimal("2000.25"));
		structure.setBonuses(new BigDecimal("500.30"));
		structure.setDeduction(new BigDecimal("750.45"));
		when(salaryStructureRepository.findById(1)).thenReturn(Optional.of(structure));

		assertThat(salaryStructureService.calculateSalary(1)).isEqualByComparingTo("11750.20");
	}

	@Test
	void getSalaryStructureReturnsNotFoundWhenStructureIsMissing() {
		when(salaryStructureRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> salaryStructureService.getSalaryStructure(99))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void calculateSalaryReturnsNotFoundWhenStructureIsMissing() {
		when(salaryStructureRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> salaryStructureService.calculateSalary(99))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
	}

}
