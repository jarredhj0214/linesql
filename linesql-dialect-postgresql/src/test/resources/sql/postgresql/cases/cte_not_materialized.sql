with q as not materialized (
  select id as user_id, status
  from app.users
)
select q.user_id
from q
where q.status = 'ACTIVE';
