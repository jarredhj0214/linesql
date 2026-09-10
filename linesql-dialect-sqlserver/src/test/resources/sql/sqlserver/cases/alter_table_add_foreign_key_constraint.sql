alter table dbo.order_items
add constraint FK_order_items_orders
foreign key (order_id)
references dbo.orders (order_id);
