update ads.employees
set salary = salary * 1.1
where salary < (select avg(salary) from ods.employee_salary_baseline);
