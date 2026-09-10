create clustered columnstore index CCI_fact_orders
on dbo.fact_orders
with (drop_existing = off, online = on);
