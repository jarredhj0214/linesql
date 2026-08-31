alter table mart.order_items
  add column seller_id bigint references mart.sellers (id) on delete set null on update no action after product_id;
