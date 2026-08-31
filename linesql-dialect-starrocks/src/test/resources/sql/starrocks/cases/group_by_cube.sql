select dt, region, sum(amount) as total_amount
from dwd.orders
group by cube(dt, region);
