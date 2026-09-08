with q as (
  select id as user_id, name as user_name
  from app.users
)
select user_id, user_name
from q
