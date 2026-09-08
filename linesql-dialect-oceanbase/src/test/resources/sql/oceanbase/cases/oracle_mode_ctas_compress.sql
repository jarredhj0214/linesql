create table mart.compressed_orders
compress
as
select id
from ods.orders
where status = 'PAID'
