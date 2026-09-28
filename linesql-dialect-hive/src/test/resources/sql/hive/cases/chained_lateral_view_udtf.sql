select j.item_id, j.item_name
from ods.events e
lateral view explode(e.items) x as item
lateral view json_tuple(item, 'id', 'name') j as item_id, item_name;
