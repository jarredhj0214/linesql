select id
from app.users
where is_active = 1 xor deleted = 1;
