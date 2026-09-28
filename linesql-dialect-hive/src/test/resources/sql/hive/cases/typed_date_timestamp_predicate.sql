select e.id
from ods.events e
where e.dt >= date '2026-09-01'
  and e.event_time < timestamp '2026-09-02 00:00:00'
