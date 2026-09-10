alter table mart.orders
add constraint ck_orders_amount check (amount >= 0 and status <> 'DELETED');
