select
  tenant_id,
  group_concat(distinct name order by created_at desc separator ',') as user_names
from app.users
group by tenant_id;
