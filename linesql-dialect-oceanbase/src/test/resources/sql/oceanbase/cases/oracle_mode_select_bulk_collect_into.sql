select id, name bulk collect into v_ids, v_names
from app.users
where created_at >= '2026-09-01';
