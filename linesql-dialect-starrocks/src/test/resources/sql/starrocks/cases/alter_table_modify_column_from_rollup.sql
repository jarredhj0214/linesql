alter table mart.orders
modify column amount decimal(20,2) sum not null default "0" comment "order amount"
after order_id
from rollup_orders
properties ("timeout" = "3600");
