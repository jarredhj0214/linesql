select vin
from ods.vehicle_metrics
where dt = '${yyyy-MM-dd}'
  and!(cpu_usage is null and mem_usage is null)
  and is_owner != 1
