select @latest_user_id := id as latest_user_id
from app.users
where status = 'ACTIVE';
