with mismatch_vin as (
  select a.vin
  from ods.vehicle_metric_daily a
  join ods.vehicle_metric_l6_daily l on a.vin = l.vin
  where a.dt = '2026-08-04'
    and not (a.valid_cmd_cnt <=> l.valid_cmd_cnt)
)
select count(1) as mismatch_vin_cnt
from mismatch_vin
