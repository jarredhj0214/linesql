create materialized view ads.mv_lineorder_flat
distributed by hash(lo_orderkey) buckets 16
order by (lo_custkey)
refresh async start('2023-07-01 10:00:00') every (interval 1 day)
as
select
  lo_orderkey,
  lo_custkey,
  sum(lo_revenue) as total_revenue
from dwd.lineorder
group by lo_orderkey, lo_custkey
