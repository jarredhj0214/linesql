select u.id as user_id
from app.users u
join app.user_profiles p on u.id <=> p.user_id
where u.status <=> p.status
