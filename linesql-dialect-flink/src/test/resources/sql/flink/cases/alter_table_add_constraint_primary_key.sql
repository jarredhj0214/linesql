alter table ods.orders
add constraint pk_orders primary key (order_id, dt) not enforced;
