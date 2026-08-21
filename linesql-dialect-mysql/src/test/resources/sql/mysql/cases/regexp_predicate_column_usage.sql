select id
from app.users
where name regexp '^A'
  and email not regexp '@test\\.com$'
  and phone rlike '^1[0-9]+'
