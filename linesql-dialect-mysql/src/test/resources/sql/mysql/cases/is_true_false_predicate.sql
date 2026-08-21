select id
from app.users
where is_active is true
  and deleted is not false;
