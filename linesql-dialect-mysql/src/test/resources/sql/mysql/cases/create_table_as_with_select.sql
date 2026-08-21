create table mart.active_users as
with src as (
  select id as user_id, name
  from app.users
  where status = 'ACTIVE'
)
select user_id, name
from src;
