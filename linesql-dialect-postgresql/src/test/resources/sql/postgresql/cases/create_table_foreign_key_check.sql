create table mart.order_items_fk (
  id bigint,
  order_id bigint,
  amount numeric,
  constraint fk_order foreign key (order_id) references mart.orders(id),
  constraint chk_amount check (amount >= 0)
)
