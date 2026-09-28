with moved_orders as (
  delete from sales.orders
  where created_at < '2024-01-01'
  returning id, customer_id, amount
)
insert into archive.orders (id, customer_id, amount)
select id, customer_id, amount
from moved_orders
returning id;
