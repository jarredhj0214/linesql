select id, name
from app.users
where status = 'ACTIVE'
order by created_at desc
limit 100 offset 20
