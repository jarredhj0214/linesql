create table mart.order_items (
  id bigint not null,
  order_id bigint not null,
  product_id bigint references mart.products (id) on delete restrict on update cascade,
  constraint fk_order_items_order
    foreign key (order_id) references mart.orders (id)
    on delete cascade on update set null
) engine = InnoDB;
