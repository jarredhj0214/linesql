select user_id
from (
  select id as User_ID
  from app.users
) u
