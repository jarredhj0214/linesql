select
  region,
  count(distinct user_id, product_id) as unique_user_products
from app.orders
group by region;
