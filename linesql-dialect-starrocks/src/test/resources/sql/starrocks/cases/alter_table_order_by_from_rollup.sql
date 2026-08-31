alter table mart.orders
order by (dt, order_id)
from rollup_orders
properties ("timeout" = "3600");
