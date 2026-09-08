create table mart.order_key_part (
  id bigint,
  tenant_id bigint,
  amount decimal(18,2)
)
partition by key (tenant_id) partitions 16
