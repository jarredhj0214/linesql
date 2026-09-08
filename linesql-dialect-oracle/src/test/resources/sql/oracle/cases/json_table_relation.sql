select e.event_id, jt.user_id, jt.amount
from ods.events e
join json_table(e.payload, '$'
  columns (
    user_id number path '$.userId',
    amount number path '$.amount'
  )
) jt on jt.user_id = e.user_id;
