insert into mart.order_items (item_id, amount, event_id)
select j.*, e.event_id
from ods.events e,
     json_table(e.payload, '$.items[*]' columns (id number path '$.id', amount number path '$.amount')) j;
