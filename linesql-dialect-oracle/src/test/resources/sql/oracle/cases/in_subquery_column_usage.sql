select u.id, u.name
from app.users u
where u.id in (
  select user_id
  from app.active_sessions
);
