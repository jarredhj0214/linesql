insert first
  into mart.high_value_orders (user_id, order_amount) values (user_id, amount)
  into mart.normal_orders (user_id, order_amount) values (user_id, amount)
select user_id, amount
from ods.orders
where status = 'PAID';
