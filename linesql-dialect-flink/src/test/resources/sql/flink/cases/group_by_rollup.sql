select region, product, sum(amount) as total_amount
from dwd.orders
group by rollup(region, product);
