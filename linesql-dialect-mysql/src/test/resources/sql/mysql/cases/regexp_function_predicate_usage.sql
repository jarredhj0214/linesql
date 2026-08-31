select id,
       find_in_set(status, allowed_statuses) as status_rank,
       regexp_substr(buyer_email, '@(.+)$') as email_domain
from app.orders
where regexp_like(buyer_email, '@')
  and regexp_replace(phone, '[^0-9]', '') <> ''
