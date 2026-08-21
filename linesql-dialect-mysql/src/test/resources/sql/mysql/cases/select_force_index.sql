select u.id as user_id, u.name
from app.users u force index for join (idx_status)
where u.status = 'ACTIVE'
