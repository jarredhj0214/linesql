alter table dbo.orders
add constraint CK_orders_amount check (amount >= 0 and status <> 'DELETED');
