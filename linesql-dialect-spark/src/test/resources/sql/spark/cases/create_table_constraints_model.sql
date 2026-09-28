create table mart.constrained_orders (
  id bigint,
  amount decimal(18, 2),
  tax decimal(18, 2) generated always as (amount * 0.1),
  constraint pk_order primary key (id) not enforced,
  constraint positive_amount check (amount >= 0),
  constraint fk_header foreign key (id)
    references mart.order_headers (id) not enforced
)
using delta
