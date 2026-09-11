with employee_tree (id, manager_id) as (
    select id, manager_id
    from dbo.employees
    where manager_id is null
    union all
    select e.id, e.manager_id
    from dbo.employees e
    join employee_tree et on e.manager_id = et.id
)
select id, manager_id
from employee_tree;
