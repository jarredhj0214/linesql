select id, name
from app.users ignore key for order by (idx_users_status)
where status = 'ACTIVE'
order by created_at desc;
