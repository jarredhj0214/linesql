create table mart.constrained_orders (
  id bigint primary key disable novalidate rely,
  external_id string,
  amount decimal(18, 2),
  constraint uq_external unique (external_id) disable novalidate,
  constraint positive_amount check (amount >= 0) disable novalidate
)
stored as orc
