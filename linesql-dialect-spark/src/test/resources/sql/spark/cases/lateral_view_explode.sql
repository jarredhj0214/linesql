select id, item
from ods.orders
lateral view explode(items) exploded as item
