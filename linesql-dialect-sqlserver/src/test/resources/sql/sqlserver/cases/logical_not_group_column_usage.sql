select u.id
from dbo.users u
where not (u.status = 'DISABLED' or u.name like 'test%')
  and u.score > 0;
