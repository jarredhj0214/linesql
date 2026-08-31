alter table mart.order_items
  add foreign key fk_order_lookup (order_id)
  references mart.orders (id)
  on delete cascade;
