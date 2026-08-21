select
  ifnull(nickname, name) as display_name,
  coalesce(phone, email) as contact
from app.users
