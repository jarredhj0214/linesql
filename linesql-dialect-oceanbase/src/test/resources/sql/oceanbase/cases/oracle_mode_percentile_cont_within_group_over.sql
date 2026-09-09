select
  e.department_id,
  percentile_cont(0.5) within group (order by e.salary)
    over (partition by e.department_id) as median_salary
from hr.employees e;
