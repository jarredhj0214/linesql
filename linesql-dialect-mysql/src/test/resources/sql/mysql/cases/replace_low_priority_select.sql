replace low_priority into mart.users (id, name)
select id, name
from app.users
where status = 'ACTIVE'
