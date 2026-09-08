create table mart.order_region
partition by list (region) (
  partition p_cn values ('CN'),
  partition p_us values ('US')
)
as
select id, region
from ods.orders
where dt = '2026-09-07'
