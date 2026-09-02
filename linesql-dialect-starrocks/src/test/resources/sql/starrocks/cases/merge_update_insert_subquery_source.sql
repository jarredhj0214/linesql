merge into ads.order_snapshot t
using (
  select order_id, user_id, amount
  from dwd.orders
  where dt = '2026-08-24'
) s
on t.order_id = s.order_id
when matched then update set amount = s.amount
when not matched then insert (order_id, user_id, amount) values (s.order_id, s.user_id, s.amount);
