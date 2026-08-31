select
  o.order_id,
  item.sku
from dwd.orders o,
lateral table(split_items(o.items)) as item(sku);
