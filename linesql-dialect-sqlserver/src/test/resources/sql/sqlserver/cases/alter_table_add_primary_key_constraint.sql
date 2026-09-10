alter table dbo.orders
add constraint PK_orders primary key (order_id, order_date);
