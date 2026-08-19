insert all
  into mart.user_order_summary (user_id, total_amount) values (user_id, amount)
  into mart.user_order_audit (user_id, audit_amount) values (user_id, amount)
select user_id, amount
from ods.orders
where status = 'PAID';
