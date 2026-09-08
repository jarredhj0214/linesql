create view ads.v_user_names
bequeath current_user
as
select id, name
from ods.users
where status = 'ACTIVE';
