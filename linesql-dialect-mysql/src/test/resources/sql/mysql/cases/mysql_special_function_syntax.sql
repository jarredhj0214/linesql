select
  extract(year from created_at) as signup_year,
  trim(both ' ' from name) as normalized_name,
  position('@' in email) as at_pos,
  substring(phone from 1 for 3) as phone_prefix
from app.users;
