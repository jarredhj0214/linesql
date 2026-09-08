create or replace view ads.v_active_users
bequeath definer
as
select id, name
from ods.users
where status = 'ACTIVE';
