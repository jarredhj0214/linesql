select u.id, u.name
from dbo.users u
order by u.id
for xml path('user'), root('users');
