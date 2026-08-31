with recursive org_hierarchy(employee_id, name, manager_id, title, level, path) as (
  select
    employee_id,
    name,
    manager_id,
    title,
    cast(1 as bigint) as level,
    name as path
  from employees
  where manager_id is null
  union all
  select
    e.employee_id,
    e.name,
    e.manager_id,
    e.title,
    oh.level + 1,
    concat(oh.path, ' -> ', e.name) as path
  from employees e
  inner join org_hierarchy oh on e.manager_id = oh.employee_id
)
select employee_id, name, title, path
from org_hierarchy
order by employee_id;
