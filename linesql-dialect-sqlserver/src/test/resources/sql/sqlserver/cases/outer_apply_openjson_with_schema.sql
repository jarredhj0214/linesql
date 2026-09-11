select e.event_id, j.user_id, j.amount
from dbo.events e
outer apply openjson(e.payload)
with (
  user_id bigint '$.userId',
  amount decimal(10,2) '$.amount'
) j
where e.dt = '20260908';
