create global temporary table tmp.session_orders
on commit delete rows
as
select id, amount
from ods.orders
where dt = '2026-09-07'
