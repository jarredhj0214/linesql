create view ads.v_active_users as
select id, name
from ods.users
where status = 'ACTIVE'
with check option constraint ck_v_active_users;
