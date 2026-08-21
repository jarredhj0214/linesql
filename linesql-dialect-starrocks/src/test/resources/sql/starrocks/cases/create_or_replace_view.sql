create or replace view ads.v_active_users as
select u.id as user_id,
       u.name
from ods.users u
where u.status = 'active'
