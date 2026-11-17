WITH original AS (SELECT e.employee_id, e.dept_id FROM hr.employees e)
(SELECT * FROM original) UNION ALL (SELECT c.employee_id, c.dept_id FROM hr.contractors c)
ORDER BY 2;
