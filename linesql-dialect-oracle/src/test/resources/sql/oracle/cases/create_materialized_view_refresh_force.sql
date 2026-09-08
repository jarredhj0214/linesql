create materialized view mart.mv_recent_orders
build immediate
refresh force on demand
as
select id, user_id, amount
from ods.orders
where status = 'PAID';
