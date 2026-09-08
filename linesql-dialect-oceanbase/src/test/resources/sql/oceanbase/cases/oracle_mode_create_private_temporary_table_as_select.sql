create private temporary table ora$ptt_session_orders
on commit preserve definition
as
select id, amount
from ods.orders
where dt = '2026-09-07'
