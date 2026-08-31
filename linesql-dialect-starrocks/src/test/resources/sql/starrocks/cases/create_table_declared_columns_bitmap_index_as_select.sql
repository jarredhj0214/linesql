create table mart.customer_snapshot_idx
(
  customer_id_new,
  first_name_new,
  index idx_customer_id (customer_id_new) using bitmap
)
as
select customer_id, first_name
from ods.customers
