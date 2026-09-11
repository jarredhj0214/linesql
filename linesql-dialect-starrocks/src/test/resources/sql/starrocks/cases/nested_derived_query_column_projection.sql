select q.user_id
from (
  select p.user_id
  from (
    select id as user_id
    from app.users
    where status = 'ACTIVE'
  ) p
) q;
