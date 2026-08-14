create view ads.v_user_cte as
with q as (
  select id as user_id, name
  from ods.users
)
select q.user_id, q.name
from q;
