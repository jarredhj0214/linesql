select region, product, count(order_id) as order_count
from dwd.orders
group by cube(region, product);
