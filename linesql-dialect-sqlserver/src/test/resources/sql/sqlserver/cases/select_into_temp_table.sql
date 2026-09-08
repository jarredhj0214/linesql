select u.id as user_id, u.name
into #active_users
from dbo.users u
where u.status = 'ACTIVE';
