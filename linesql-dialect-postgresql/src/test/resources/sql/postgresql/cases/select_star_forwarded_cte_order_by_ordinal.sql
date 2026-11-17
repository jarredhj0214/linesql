WITH original AS (SELECT e.employee_id, e.dept_id FROM hr.employees e),
     forwarded AS (SELECT * FROM original)
SELECT * FROM forwarded ORDER BY 2;
