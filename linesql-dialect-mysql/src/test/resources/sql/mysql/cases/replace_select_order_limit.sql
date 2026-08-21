replace into mart.top_users (user_id, score)
select id, score
from app.users
where status = 'ACTIVE'
order by score desc
limit 100;
