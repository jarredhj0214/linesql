select x.item_id, x.item_name
from ods.orders o
lateral view inline(o.items) x as item_id, item_name;
