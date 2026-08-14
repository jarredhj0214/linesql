select
  cast(u.id as char) as user_id_text,
  coalesce(u.name, u.nickname) as display_name,
  u.price * u.quantity as amount
from app.users u;
