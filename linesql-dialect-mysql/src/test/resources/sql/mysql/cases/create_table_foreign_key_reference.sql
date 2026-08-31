create table mart.orders (
  id bigint not null,
  user_id bigint not null,
  amount decimal(18, 2),
  primary key (id),
  constraint fk_orders_user foreign key (user_id) references mart.users (id)
) engine = InnoDB
