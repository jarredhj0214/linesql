select o.id, u.item
from ods.orders o, unnest(o.items) u (item)
