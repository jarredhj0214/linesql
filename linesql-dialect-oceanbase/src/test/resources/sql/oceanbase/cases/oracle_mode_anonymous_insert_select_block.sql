begin
  insert into mart.order_summary(order_id, amount)
  select order_id, amount
  from ods.orders
  where status = 'PAID';
end
