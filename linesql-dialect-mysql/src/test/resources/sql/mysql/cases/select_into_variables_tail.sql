select id, name
from app.users
where status = 'ACTIVE'
into @user_id, @user_name
