with order_amounts as (
  select user_id, amount
  from app.orders
  where status = 'PAID'
)
update app.users u
join order_amounts o on u.id = o.user_id
set u.total_amount = o.amount
where u.status = 'ACTIVE';
