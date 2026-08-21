select
  u.`department_id`,
  d.`department_name`
from ods.users u
left join dim.departments d
  on u.department_id = d.department_id
