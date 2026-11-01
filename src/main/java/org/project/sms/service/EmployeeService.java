package org.project.sms.service;

import java.math.BigDecimal;
import java.util.List;

import org.project.sms.dto.EmployeeDto;
import org.project.sms.dto.EmployeeDeleteResponseDto;
import org.project.sms.dto.SalaryStructureDto;
import org.project.sms.entity.Employee;
import org.project.sms.entity.SalaryStructure;
import org.project.sms.repository.DepartmentRepository;
import org.project.sms.repository.EmployeeRepository;
import org.project.sms.repository.SalaryStructureRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeService {

	private final EmployeeRepository employeeRepository;
	private final SalaryStructureService salaryStructureService;
	private final DepartmentRepository departmentRepository;
	private final SalaryStructureRepository salaryStructureRepository;

	@Transactional
	public EmployeeDto createEmployee(EmployeeDto request) {
		var salary = validatedSalary(request);
		var employee = new Employee();
		applyEmployee(employee, request, salary);
		employee = employeeRepository.save(employee);

		var salaryStructure = new SalaryStructure();
		salaryStructure.setEmpId(employee.getId());
		applySalaryStructure(salaryStructure, request.getSalaryStructureDto());
		salaryStructureRepository.save(salaryStructure);

		return toDto(employee, salaryStructure);
	}

	@Transactional
	public EmployeeDto updateEmployee(Integer id, EmployeeDto request) {
		if (id == null || id <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee ID must be positive");
		}
		var employee = employeeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
		var salaryStructure = salaryStructureRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salary structure not found"));
		var salary = validatedSalary(request);

		// Both entities are managed; dirty checking updates their existing rows on commit.
		applyEmployee(employee, request, salary);
		applySalaryStructure(salaryStructure, request.getSalaryStructureDto());
		return toDto(employee, salaryStructure);
	}

	@Transactional
	public EmployeeDeleteResponseDto deleteEmployee(Integer id) {
		if (id == null || id <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee ID must be positive");
		}
		var employee = employeeRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
		salaryStructureRepository.deleteById(id);
		// Flush the dependent deletion before the employee; both remain in the same transaction.
		salaryStructureRepository.flush();
		employeeRepository.delete(employee);
		return new EmployeeDeleteResponseDto(id, "Employee Deleted Successfully");
	}

	private BigDecimal validatedSalary(EmployeeDto request) {
		if (!departmentRepository.existsById(request.getDepartmentId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found");
		}
		var structure = request.getSalaryStructureDto();
		var salary = structure.getFixedPay().add(structure.getVariablePay())
				.add(structure.getBonuses()).subtract(structure.getDeduction()).setScale(2);
		if (salary.signum() < 0 || salary.precision() - salary.scale() > 36) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Calculated salary must be nonnegative and fit the supported monetary range");
		}
		if (request.getSalary() != null && request.getSalary().compareTo(salary) != 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salary does not match the salary structure");
		}
		return salary;
	}

	private void applyEmployee(Employee employee, EmployeeDto request, BigDecimal salary) {
		employee.setName(request.getName().strip());
		employee.setDepartmentId(request.getDepartmentId());
		employee.setCountry(request.getCountry().strip());
		employee.setSalary(salary);
	}

	private void applySalaryStructure(SalaryStructure salaryStructure, SalaryStructureDto structure) {
		salaryStructure.setFixedPay(structure.getFixedPay());
		salaryStructure.setVariablePay(structure.getVariablePay());
		salaryStructure.setBonuses(structure.getBonuses());
		salaryStructure.setDeduction(structure.getDeduction());
	}

	private EmployeeDto toDto(Employee employee, SalaryStructure structure) {
		return new EmployeeDto(employee.getId(), employee.getName(), employee.getDepartmentId(), employee.getSalary(),
				employee.getCountry(), new SalaryStructureDto(structure.getEmpId(), structure.getFixedPay(),
						structure.getVariablePay(), structure.getBonuses(), structure.getDeduction()));
	}

	@Transactional(readOnly = true)
	public List<EmployeeDto> getEmp(Integer depId, Pageable pagination) {
		return employeeRepository.findByDepartmentId(depId, pagination)
				.map(this::toDto)
				.getContent();
	}

	private EmployeeDto toDto(Employee employee) {
		return new EmployeeDto(employee.getId(), employee.getName(), employee.getDepartmentId(),
				employee.getSalary(), employee.getCountry(),
				salaryStructureService.getSalaryStructure(employee.getId()));
	}

}
