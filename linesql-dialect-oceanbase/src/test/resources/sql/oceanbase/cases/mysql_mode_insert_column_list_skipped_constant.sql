insert into mart.user_summary (load_dt, user_id)
select current_date(), u.id
from app.users u
where u.status = 'ACTIVE';
