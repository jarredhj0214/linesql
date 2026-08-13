select id, item
from ods.orders o, unnest(o.items) u (item)
