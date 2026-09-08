insert into @active_users(id, name)
select id, name
from ods.users
where status = 'ACTIVE';
