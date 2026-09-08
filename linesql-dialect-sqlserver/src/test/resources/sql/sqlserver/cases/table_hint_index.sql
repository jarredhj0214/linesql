select u.id
from dbo.users with (nolock, index(IX_users_status)) u
where u.status = 'ACTIVE';
