update ads.employees
set salary = (select max(target_salary) from ods.employee_salary_baseline)
where department_id = 10;
