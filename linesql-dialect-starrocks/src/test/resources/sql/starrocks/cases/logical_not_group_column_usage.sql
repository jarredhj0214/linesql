select u.id
from ods.users u
where not (u.status = 'DISABLED' or u.name like 'test%')
  and u.score > 0;
