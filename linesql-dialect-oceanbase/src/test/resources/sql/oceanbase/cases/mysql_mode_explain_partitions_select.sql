explain partitions select id as user_id
from app.users partition (p202609)
where status = 'ACTIVE'
