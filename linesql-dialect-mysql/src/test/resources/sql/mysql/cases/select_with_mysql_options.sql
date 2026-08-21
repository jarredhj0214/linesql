select high_priority sql_calc_found_rows id as user_id, name
from app.users
where status = 'ACTIVE'
limit 10
