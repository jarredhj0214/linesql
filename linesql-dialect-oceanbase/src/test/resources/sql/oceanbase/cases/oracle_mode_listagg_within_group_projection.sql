select
  d.dept_id,
  listagg(d.emp_name, ',') within group (order by d.hire_date desc) as employee_names
from hr.department_employees d
group by d.dept_id;
