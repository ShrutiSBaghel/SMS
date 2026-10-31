-- Run after the application has created its PostgreSQL tables:
-- psql -h localhost -U postgres -d SMS -v ON_ERROR_STOP=1 -f scripts/seed.sql
-- Requires empty department, employee, and salary_structure tables.
-- spring.jpa.hibernate.ddl-auto=create erases this data on the next application startup.
-- Salary amounts are synthetic: fixed pay 30,000-150,000, variable pay 0-20,000,
-- bonuses 0-10,000, and deduction 0-10,000, each rounded to two decimal places.

BEGIN;

-- Prevent concurrent writes between the empty-table check and the inserts.
LOCK TABLE department, employee, salary_structure IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM department)
		OR EXISTS (SELECT 1 FROM employee)
		OR EXISTS (SELECT 1 FROM salary_structure) THEN
		RAISE EXCEPTION 'Seed requires empty department, employee, and salary_structure tables. No data was changed.';
	END IF;
END;
$$;

CREATE TEMPORARY TABLE seed_department_distribution (
	name text PRIMARY KEY,
	employee_count integer NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_department_distribution (name, employee_count)
VALUES
	('Finance', 400),
	('HR', 500),
	('Operations', 4500),
	('Engineers', 1500),
	('Marketing', 800),
	('Sales', 2000),
	('IT & Legal', 300);

-- Let PostgreSQL generate IDs rather than assuming a starting sequence value.
INSERT INTO department (name)
SELECT name
FROM seed_department_distribution;

-- Materialize random components once so both tables use identical salary values.
CREATE TEMPORARY TABLE seed_employee_data ON COMMIT DROP AS
SELECT
	'Employee ' || lpad(row_number() OVER (ORDER BY d.id, series.ordinal)::text, 5, '0') AS name,
	d.id AS department_id,
	(ARRAY['India', 'United States', 'United Kingdom', 'Canada', 'Germany', 'Australia'])
		[1 + floor(random() * 6)::integer] AS country,
	round((30000 + random() * 120000)::numeric, 2) AS fixed_pay,
	round((random() * 20000)::numeric, 2) AS variable_pay,
	round((random() * 10000)::numeric, 2) AS bonuses,
	round((random() * 10000)::numeric, 2) AS deduction
FROM seed_department_distribution distribution
JOIN department d ON d.name = distribution.name
CROSS JOIN LATERAL generate_series(1, distribution.employee_count) AS series(ordinal);

INSERT INTO employee (name, department_id, salary, country)
SELECT name, department_id, fixed_pay + variable_pay + bonuses - deduction, country
FROM seed_employee_data
ORDER BY name;

INSERT INTO salary_structure (emp_id, fixed_pay, variable_pay, bonuses, deduction)
SELECT e.id, seed.fixed_pay, seed.variable_pay, seed.bonuses, seed.deduction
FROM seed_employee_data seed
JOIN employee e ON e.name = seed.name AND e.department_id = seed.department_id;

DO $$
BEGIN
	IF (SELECT count(*) FROM department) <> 7
		OR (SELECT count(*) FROM employee) <> 10000
		OR (SELECT count(*) FROM salary_structure) <> 10000 THEN
		RAISE EXCEPTION 'Seed validation failed: incorrect department, employee, or salary structure count.';
	END IF;

	IF EXISTS (
		SELECT distribution.name
		FROM seed_department_distribution distribution
		LEFT JOIN department d ON d.name = distribution.name
		LEFT JOIN employee e ON e.department_id = d.id
		GROUP BY distribution.name, distribution.employee_count
		HAVING count(e.id) <> distribution.employee_count
	) THEN
		RAISE EXCEPTION 'Seed validation failed: incorrect employee distribution.';
	END IF;

	IF EXISTS (
		SELECT 1
		FROM employee e
		LEFT JOIN department d ON d.id = e.department_id
		LEFT JOIN salary_structure structure ON structure.emp_id = e.id
		WHERE d.id IS NULL OR structure.emp_id IS NULL
			OR e.salary IS DISTINCT FROM
				(structure.fixed_pay + structure.variable_pay + structure.bonuses - structure.deduction)
	) OR EXISTS (
		SELECT 1
		FROM salary_structure structure
		LEFT JOIN employee e ON e.id = structure.emp_id
		WHERE e.id IS NULL
	) THEN
		RAISE EXCEPTION 'Seed validation failed: invalid references or inconsistent salaries.';
	END IF;
END;
$$;

SELECT d.name AS department, count(e.id) AS employees,
	round(count(e.id) * 100.0 / 10000, 2) AS percentage
FROM department d
JOIN employee e ON e.department_id = d.id
GROUP BY d.id, d.name
ORDER BY d.id;

COMMIT;
