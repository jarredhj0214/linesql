select
  u.id as user_id,
  u.name as user_name
from ${source_schema}.${source_table} u
where u.dt = '${yyyy-MM-dd}'

