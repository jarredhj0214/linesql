select user_id
from ods.active_users
minus distinct
select user_id
from ods.blocked_users
