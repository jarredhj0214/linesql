select j.order_id, j.has_items, j.items
from ods.orders o,
     json_table(o.payload format json, '$' error on error null on empty
       columns (
         order_id number path '$.id' default 0 on empty,
         has_items number exists path '$.items' false on error,
         items clob format json with conditional array wrapper path '$.items[*]' empty array on error
       )
     ) j;
