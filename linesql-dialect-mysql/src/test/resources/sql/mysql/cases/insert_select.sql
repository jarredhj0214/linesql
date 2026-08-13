insert into mart.user_summary (user_id, user_name)
select id, name
from app.users
