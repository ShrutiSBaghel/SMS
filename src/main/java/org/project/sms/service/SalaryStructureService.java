package org.project.sms.service;

import java.math.BigDecimal;

import org.project.sms.dto.SalaryStructureDto;
import org.project.sms.repository.SalaryStructureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SalaryStructureService {

	private final SalaryStructureRepository salaryStructureRepository;

	@Transactional(readOnly = true)
	public SalaryStructureDto getSalaryStructure(Integer empId) {
		var structure = salaryStructureRepository.findById(empId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"Salary structure not found for employee " + empId));

		return new SalaryStructureDto(structure.getEmpId(), structure.getFixedPay(),
				structure.getVariablePay(), structure.getBonuses(), structure.getDeduction());
	}

	@Transactional(readOnly = true)
	public BigDecimal calculateSalary(Integer empId) {
		var structure = getSalaryStructure(empId);

		return structure.getFixedPay()
				.add(structure.getVariablePay())
				.add(structure.getBonuses())
				.subtract(structure.getDeduction());
	}

}
