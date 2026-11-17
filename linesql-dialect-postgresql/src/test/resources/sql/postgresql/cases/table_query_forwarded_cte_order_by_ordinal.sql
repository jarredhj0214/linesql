WITH original AS (SELECT e.employee_id, e.dept_id FROM hr.employees e),
     forwarded AS (TABLE original)
TABLE forwarded ORDER BY 2;
