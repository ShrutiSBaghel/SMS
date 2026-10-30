package org.project.sms.repository;

import java.util.List;

import org.project.sms.dto.EmployeeFilterRequest;
import org.project.sms.entity.Employee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

	Slice<Employee> findByDepartmentId(Integer depId, Pageable pageable);

	default List<Employee> filterEmployee(EmployeeFilterRequest request) {
		// TODO: Implement the employee filter query once the filter fields are defined.
		throw new UnsupportedOperationException("Employee filtering is not implemented yet");
	}

}
