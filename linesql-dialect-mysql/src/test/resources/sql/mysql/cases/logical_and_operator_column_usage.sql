select id
from app.users
where is_active && !deleted;
