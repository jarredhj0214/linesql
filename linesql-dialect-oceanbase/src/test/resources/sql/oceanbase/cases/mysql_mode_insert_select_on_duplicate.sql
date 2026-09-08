insert into mart.user_summary (user_id, user_name)
select id, name
from app.users_delta
on duplicate key update user_name = values(user_name)
