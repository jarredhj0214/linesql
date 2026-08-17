select candidate, id
from (
  select stack(2,
               'trace_id', trace_id,
               'request_id', request_id) as (candidate, id)
  from ods.request_logs
) q
where id is not null
