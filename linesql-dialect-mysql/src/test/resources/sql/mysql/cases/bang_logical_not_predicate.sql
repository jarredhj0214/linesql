select id
from app.users
where !(status = 'DELETED' or name like 'test%')
  and score > 0
