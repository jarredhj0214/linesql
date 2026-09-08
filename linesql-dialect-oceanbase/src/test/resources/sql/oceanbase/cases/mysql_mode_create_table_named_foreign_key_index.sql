create table mart.order_items (
  id bigint primary key,
  order_id bigint not null,
  constraint fk_order_items_order foreign key fk_order_lookup (order_id)
    references mart.orders (id)
    on delete cascade
    on update restrict
);
