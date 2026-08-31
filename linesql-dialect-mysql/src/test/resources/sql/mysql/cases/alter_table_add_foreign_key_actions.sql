alter table mart.order_items
  add constraint fk_order_items_product
  foreign key (product_id) references mart.products (id)
  on delete restrict on update cascade;
