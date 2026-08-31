select user_id
from ods.users
where name like 'A\\_%' escape '\\'
  and email regexp '.*@example\\.com'
  and phone not rlike '^000'
