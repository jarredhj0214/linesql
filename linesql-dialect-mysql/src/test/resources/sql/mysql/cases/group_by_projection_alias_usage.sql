select lower(region) as region_key, count(order_id) as order_count
from app.orders
group by region_key;
