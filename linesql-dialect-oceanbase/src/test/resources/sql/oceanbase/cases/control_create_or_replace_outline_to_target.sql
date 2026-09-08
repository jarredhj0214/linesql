create or replace outline outline_orders_hint
on select /*+ leading(o u) */ o.id from app.orders o join app.users u on o.user_id = u.id
to select o.id from app.orders o join app.users u on o.user_id = u.id;
