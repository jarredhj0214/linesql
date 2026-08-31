select id, name
from app.users
where name not like 'test%'
  and email not regexp '@example\\.com$'
  and phone not rlike '^000'
