select
  substring_index(email, '@', -1) as email_domain,
  locate('-', sku_code) as sku_dash_pos,
  insert(phone, 4, 4, '****') as masked_phone,
  repeat(prefix, retry_count) as repeated_prefix
from app.users
where locate('vip', tags) > 0
