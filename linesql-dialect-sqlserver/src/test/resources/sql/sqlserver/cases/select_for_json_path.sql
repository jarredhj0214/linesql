select u.id, u.name
from dbo.users u
where u.status = 'ACTIVE'
for json path, root('users');
