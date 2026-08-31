select user_id
from ods.users
where is_active is true
  and deleted is not false
