create event app.rollup_daily_users
on schedule every 1 day
do insert into mart.daily_users(user_id, user_name)
select id, name
from app.users
where status = 'ACTIVE';
