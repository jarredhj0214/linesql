select s.item_id, s.item_name
from ods.orders o
lateral view stack(
  2,
  o.primary_item_id,
  o.primary_item_name,
  o.secondary_item_id,
  o.secondary_item_name
) s as item_id, item_name;
