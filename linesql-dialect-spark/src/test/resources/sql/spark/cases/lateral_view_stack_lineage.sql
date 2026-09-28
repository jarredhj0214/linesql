select s.candidate, s.id
from ods.request_logs l
lateral view stack(
  2,
  l.trace_type,
  l.trace_id,
  l.request_type,
  l.request_id
) s as candidate, id;
