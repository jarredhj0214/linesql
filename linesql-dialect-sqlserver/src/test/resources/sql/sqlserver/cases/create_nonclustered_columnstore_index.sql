create nonclustered columnstore index NCCI_fact_orders_amount
on dbo.fact_orders (order_id, amount)
where amount > 0;
