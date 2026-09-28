create materialized view if not exists mart.user_totals
disable rewrite
stored as orc
tblproperties ('transactional' = 'true')
as
select
  user_id,
  sum(amount) as total_amount
from ods.orders
group by user_id
