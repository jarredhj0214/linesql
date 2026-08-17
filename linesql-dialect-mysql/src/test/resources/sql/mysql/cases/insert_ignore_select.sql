insert ignore into mart.user_summary (user_id, user_name)
select u.id, u.name
from app.users u;
