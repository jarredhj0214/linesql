alter table mart.order_items
add constraint fk_order_items_orders
foreign key (order_id)
references mart.orders (order_id)
deferrable initially deferred;
