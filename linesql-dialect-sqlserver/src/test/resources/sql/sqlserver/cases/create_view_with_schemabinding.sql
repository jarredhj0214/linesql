create view dbo.v_active_users
with schemabinding
as
select u.id, u.name
from dbo.users u
where u.status = 'ACTIVE';
