select u.user_id,
       u.product_id
from ods.user_products u
where (u.user_id, u.product_id) not in (
  select user_id, product_id
  from dwd.blocked_products
  where reason = 'RISK'
)
