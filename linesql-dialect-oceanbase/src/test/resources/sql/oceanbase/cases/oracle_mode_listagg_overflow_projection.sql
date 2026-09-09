select
  d.dept_id,
  listagg(d.emp_name, ',' on overflow truncate '...' with count)
    within group (order by d.hire_date desc) as employee_names
from hr.department_employees d
group by d.dept_id;
