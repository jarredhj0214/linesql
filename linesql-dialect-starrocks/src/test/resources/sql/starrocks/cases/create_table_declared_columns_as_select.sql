create table mart.customer_snapshot (customer_id_new, first_name_new)
as
select customer_id, first_name
from ods.customers
