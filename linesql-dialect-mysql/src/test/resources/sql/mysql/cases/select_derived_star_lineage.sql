select q.*
from (
  select id as user_id, name
  from app.users
) q;
