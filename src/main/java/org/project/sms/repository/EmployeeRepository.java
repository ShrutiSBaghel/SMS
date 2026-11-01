package org.project.sms.repository;

import java.util.List;

import org.project.sms.entity.Employee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

	@Query(value = """
			SELECT coalesce(cast(d.id AS text), 'unassigned') AS "groupKey",
			       coalesce(nullif(trim(d.name), ''), 'Unassigned') AS label,
			       coalesce(sum(e.salary), 0) AS "totalSalary",
			       count(*) AS "employeeCount",
			       count(*) - count(e.salary) AS "missingSalaryCount"
			FROM employee e LEFT JOIN department d ON e.department_id = d.id
			GROUP BY d.id, d.name
			ORDER BY "totalSalary" DESC, label, "groupKey"
			""", nativeQuery = true)
	List<SalaryDistributionProjection> salaryByDepartment();

	@Query(value = """
			SELECT coalesce(lower(nullif(trim(country), '')), 'unassigned') AS "groupKey",
			       coalesce(min(nullif(trim(country), '')), 'Unassigned') AS label,
			       coalesce(sum(salary), 0) AS "totalSalary",
			       count(*) AS "employeeCount",
			       count(*) - count(salary) AS "missingSalaryCount"
			FROM employee
			GROUP BY lower(nullif(trim(country), ''))
			ORDER BY "totalSalary" DESC, label
			""", nativeQuery = true)
	List<SalaryDistributionProjection> salaryByCountry();

	Slice<Employee> findByDepartmentId(Integer depId, Pageable pageable);

	long countByDepartmentId(Integer depId);


}
