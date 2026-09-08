create format outline outline_format_orders
on select * from app.orders where tenant_id = ?;
