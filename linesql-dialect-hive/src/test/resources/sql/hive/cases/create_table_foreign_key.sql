create table mart.order_items (
  order_id bigint,
  item_id bigint,
  constraint fk_order foreign key (order_id)
    references mart.orders (id)
    disable novalidate rely
)
stored as orc
