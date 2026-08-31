insert into ${target_schema}.${target_table} (user_id, user_name)
select
  u.id,
  u.name
from ${source_schema}.${source_table} u
where u.status = 'ACTIVE'

