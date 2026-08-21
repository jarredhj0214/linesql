select
  u.`department_id`,
  d.`department_name`
from app.users u
left join app.departments d
  on u.department_id = d.department_id
