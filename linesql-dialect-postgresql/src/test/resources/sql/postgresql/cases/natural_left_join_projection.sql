select u.id as user_id, p.plan_name
from app.users u
natural left outer join billing.plans p
where p.enabled = true
