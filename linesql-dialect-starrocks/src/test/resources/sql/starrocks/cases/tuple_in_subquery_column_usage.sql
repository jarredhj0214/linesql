select u.user_id,
       u.product_id
from ods.user_products u
where (u.user_id, u.product_id) in (
  select user_id, product_id
  from dwd.active_products
  where status = 'ACTIVE'
)
