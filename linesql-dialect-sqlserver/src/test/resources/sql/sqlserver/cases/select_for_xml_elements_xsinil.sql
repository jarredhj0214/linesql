select u.id, u.name
from dbo.users u
where u.status = 'ACTIVE'
order by u.id
for xml path('user'), root('users'), type, elements xsinil;
