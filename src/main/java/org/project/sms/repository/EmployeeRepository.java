package org.project.sms.repository;

import org.project.sms.entity.Employee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

	Slice<Employee> findByDepartmentId(Integer depId, Pageable pageable);

	long countByDepartmentId(Integer depId);


}
