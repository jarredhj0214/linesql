create outline outline_orders_lookup
on select /*+ index(o idx_order_id) */ * from app.orders o where o.order_id = 1;
