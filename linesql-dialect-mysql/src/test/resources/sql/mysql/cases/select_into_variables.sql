select id, name
into @user_id, @user_name
from app.users
where status = 'ACTIVE';
