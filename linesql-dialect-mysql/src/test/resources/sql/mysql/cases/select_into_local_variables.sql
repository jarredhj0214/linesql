select id, name
into v_user_id, v_user_name
from app.users
where status = 'ACTIVE';
