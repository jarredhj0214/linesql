delete u.*, s.*
from mart.users u
join staging.users_to_delete s on u.id = s.user_id
where s.batch_id = 20260828;
