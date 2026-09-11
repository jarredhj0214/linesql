insert into public.orders(id, amount)
overriding user value
select order_id, amount
from staging.orders_delta
where amount > 0;
