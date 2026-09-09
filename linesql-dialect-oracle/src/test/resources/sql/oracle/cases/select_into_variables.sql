select id, name into v_id, v_name
from app.users
where status = 'ACTIVE';
