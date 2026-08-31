with recursive employee_path(id, manager_id) as (
  select id, manager_id
  from ods.employees
  where manager_id is null
  union all
  select e.id, e.manager_id
  from ods.employees e
  join employee_path p on e.manager_id = p.id
)
select id, manager_id
from employee_path
