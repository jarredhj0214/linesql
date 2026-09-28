select id
from ods.runtime_metrics
where !(cpu_usage is null and mem_usage is null)
  and enabled is true
  and archived is not false
