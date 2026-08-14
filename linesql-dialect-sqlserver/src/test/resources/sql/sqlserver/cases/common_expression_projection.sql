select
  cast(u.id as varchar) as user_id_text,
  coalesce(u.name, u.nickname) as display_name,
  u.price * u.quantity as amount
from dbo.users u;
